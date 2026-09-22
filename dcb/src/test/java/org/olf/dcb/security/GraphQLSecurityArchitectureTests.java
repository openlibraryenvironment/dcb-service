package org.olf.dcb.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The structural guard on GraphQL authorisation — the counterpart to
 * {@link ApiSecurityArchitectureTests}, which cannot see any of this.
 *
 * <h2>Why a second guard was needed</h2>
 *
 * {@code ApiSecurityArchitectureTests} enforces that every REST route carries an explicit
 * {@code @Secured}. It reads compiled {@code @Controller} bean definitions — and a GraphQL
 * data fetcher is not a controller, so the whole of {@code /graphql} sat outside it. That
 * endpoint is behind {@code isAuthenticated()} and nothing more, so each fetcher is its own
 * access-control decision, and four MUTATIONS had shipped with no decision at all:
 * {@code createAgencyGroup}, {@code addAgencyToGroup}, {@code createLibraryGroup} and
 * {@code addLibraryToGroup} were reachable by every principal the realm issues a token to.
 * This test fails on all four before the fix, which is the only evidence that it is a guard
 * rather than decoration.
 *
 * <h2>Why it reads source rather than bytecode</h2>
 *
 * The thing being asserted is "a human decided who may call this", and the decision is a
 * statement inside a lambda in a 1,200-line factory class. {@code DataFetchers} takes
 * thirty-one repositories, so the fetchers cannot be instantiated in a unit test; the
 * lambdas are anonymous, so reflection cannot find them. Reading the source is crude, and
 * it is the only thing here that actually catches the defect.
 *
 * <h2>What it does NOT assert</h2>
 *
 * Not that the role set is the RIGHT one — that is a judgement, and it belongs in review.
 * Only that one was chosen. Nested field resolvers are also out of scope, deliberately:
 * they are reachable only through a top-level field, so guarding every entry point guards
 * the traversal.
 */
class GraphQLSecurityArchitectureTests {

	/** {@code .dataFetcher("fieldName", expression)} in the runtime wiring. */
	private static final Pattern WIRING = Pattern.compile(
		"\\.dataFetcher\\(\"([A-Za-z0-9_]+)\"\\s*,\\s*([A-Za-z0-9_.]+?)(\\(\\))?\\s*\\)");

	/**
	 * {@code SomeDataFetcher someDataFetcher} in the factory — its fetchers arrive as
	 * parameters of the {@code graphQL(...)} bean method rather than as fields, so this
	 * matches the declaration wherever it appears. Anchored on the {@code DataFetcher}
	 * suffix, which is the naming convention the whole package follows.
	 *
	 * The plural is matched too, and is not cosmetic: a holder that groups several fetchers
	 * takes it ({@code DataFetchers}, {@code LibraryUserDataFetchers}), and matching only
	 * the singular left every fetcher on a holder unresolvable. The prefix is optional for
	 * the same reason — {@code DataFetchers} is the suffix and nothing else.
	 */
	private static final Pattern FETCHER_DECLARATION = Pattern.compile(
		"\\b((?:[A-Z][A-Za-z0-9_]*)?DataFetchers?)\\s+([a-z][A-Za-z0-9_]*)\\b");

	private static final Path GRAPHQL = sourceRoot();

	/**
	 * An authorisation decision, in either of the two forms this codebase uses: the shared
	 * helper, or the hand-rolled role check that predates it.
	 */
	private static boolean decidesAccess(String body) {
		return body.contains("GraphQLRoles.require(")
			|| body.contains("roles.contains(");
	}

	@Test
	@DisplayName("every top-level Mutation fetcher decides who may call it")
	void everyMutationFetcherDecidesWhoMayCallIt() {
		assertEveryFetcherDecides("Mutation");
	}

	@Test
	@DisplayName("every top-level Query fetcher decides who may call it")
	void everyQueryFetcherDecidesWhoMayCallIt() {
		// A read is not a lesser decision than a write here. /graphql returns patron
		// requests, patron identities, host LMS configuration and the people who staff
		// every member library.
		assertEveryFetcherDecides("Query");
	}

	@Test
	@DisplayName("every Query and Mutation field in the schema is actually wired")
	void everySchemaFieldIsWired() {
		// A field declared in the schema and never wired resolves to null rather than to
		// an error, so it fails silently. It also means this test's inventory is only as
		// complete as the wiring, and an unwired field would be invisible to the two
		// assertions above rather than failing them.
		for (var section : List.of("Query", "Mutation")) {
			final var declared = schemaFieldsOf(section);
			final var wired = wiringFor(section).keySet();

			final var missing = new LinkedHashSet<>(declared);
			missing.removeAll(wired);

			assertTrue(missing.isEmpty(),
				section + " fields declared in schema.graphqls but never wired in GraphQLFactory: " + missing);
		}
	}

	private void assertEveryFetcherDecides(String section) {
		final var factory = read(GRAPHQL.resolve("GraphQLFactory.java"));
		final var fieldTypes = declaredFieldTypes(factory);

		final var unguarded = new ArrayList<String>();

		for (var entry : wiringFor(section).entrySet()) {
			final var field = entry.getKey();
			final var expression = entry.getValue();

			// Two wiring shapes, and the holder is never named here: `holder.method()` for a
			// fetcher grouped onto a holder bean, a bare bean name for a standalone fetcher.
			// Hardcoding one holder's name is how this guard went blind on the second one.
			final var dot = expression.indexOf('.');
			final var bean = dot < 0 ? expression : expression.substring(0, dot);
			final var type = fieldTypes.get(bean);

			if (type == null) {
				fail(section + "." + field + " is wired to '" + expression
					+ "', whose declaration this test cannot find in GraphQLFactory.");
			}

			final var source = GRAPHQL.resolve(type + ".java");

			if (!Files.isRegularFile(source)) {
				fail(section + "." + field + " resolves to " + type
					+ ", which is not a source file in org.olf.dcb.graphql.");
			}

			final String body;

			if (dot < 0) {
				// A standalone fetcher: the class is the decision.
				body = read(source);
			}
			else {
				final var method = expression.substring(dot + 1);
				body = methodBody(read(source), method);

				if (body == null) {
					fail(section + "." + field + " is wired to " + type + "." + method
						+ ", which this test cannot find. Rename or restructure and this guard goes blind.");
				}
			}

			if (!decidesAccess(body)) {
				unguarded.add(section + "." + field + " -> " + expression);
			}
		}

		assertTrue(unguarded.isEmpty(),
			"/graphql is behind isAuthenticated() and nothing more, so a fetcher with no role check is "
				+ "reachable by every principal the realm issues a token to - including DISCOVERY_SERVICE, "
				+ "held by discovery backends that may be third party. These decide nothing:\n  "
				+ String.join("\n  ", unguarded));
	}

	/** field name -> the expression it is wired to, for one section of the runtime wiring. */
	private static Map<String, String> wiringFor(String section) {
		final var factory = read(GRAPHQL.resolve("GraphQLFactory.java"));
		final var block = sectionOf(factory, section);
		final var matcher = WIRING.matcher(block);
		final var wiring = new LinkedHashMap<String, String>();

		while (matcher.find()) {
			wiring.put(matcher.group(1), matcher.group(2));
		}

		assertTrue(wiring.size() > 10,
			"Only found " + wiring.size() + " " + section + " fetchers in GraphQLFactory. "
				+ "The wiring has been restructured and this guard is no longer reading it.");

		return wiring;
	}

	/**
	 * The runtime wiring for one type, from its {@code newTypeWiring}/{@code .type(} opener
	 * to the start of the next one. Both spellings are in use in the factory.
	 */
	private static String sectionOf(String factory, String section) {
		final var markers = List.of(
			".type(TypeRuntimeWiring.newTypeWiring(\"" + section + "\")",
			".type(\"" + section + "\"");

		int start = -1;
		for (var marker : markers) {
			start = factory.indexOf(marker);
			if (start >= 0) {
				break;
			}
		}

		if (start < 0) {
			fail("No runtime wiring block for " + section + " in GraphQLFactory.");
		}

		final var next = factory.indexOf(".type(", start + 6);

		return next < 0 ? factory.substring(start) : factory.substring(start, next);
	}

	/** injected fetcher name -> its declared type. */
	private static Map<String, String> declaredFieldTypes(String factory) {
		final var matcher = FETCHER_DECLARATION.matcher(factory);
		final var types = new LinkedHashMap<String, String>();

		while (matcher.find()) {
			types.putIfAbsent(matcher.group(2), matcher.group(1));
		}

		return types;
	}

	/**
	 * The body of a named method, by brace matching from its declaration.
	 *
	 * @return null when no such method is declared
	 */
	private static String methodBody(String source, String method) {
		final var declaration = Pattern.compile("\\s" + Pattern.quote(method) + "\\s*\\(\\s*\\)\\s*\\{");
		final var matcher = declaration.matcher(source);

		if (!matcher.find()) {
			return null;
		}

		int depth = 0;
		for (int i = matcher.end() - 1; i < source.length(); i++) {
			final var c = source.charAt(i);

			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;

				if (depth == 0) {
					return source.substring(matcher.end(), i);
				}
			}
		}

		return source.substring(matcher.end());
	}

	/** The field names declared under {@code type Query} / {@code type Mutation}. */
	private static Set<String> schemaFieldsOf(String section) {
		final var schema = read(GRAPHQL.getParent().getParent().getParent().getParent()
			.getParent().resolve("resources/schema.graphqls"));

		final var start = schema.indexOf("type " + section + " {");
		assertTrue(start >= 0, "No `type " + section + "` in schema.graphqls");

		final var end = schema.indexOf("\n}", start);

		// Argument lists removed first. patronRequestStatistics(...) wraps across two
		// lines, so a line-anchored match would read `statusCodes:` as a field of Query.
		final var block = withoutArgumentLists(schema.substring(start, end));

		final var fields = new LinkedHashSet<String>();
		final var matcher = Pattern.compile("(?m)^\\s*([a-zA-Z][A-Za-z0-9_]*)\\s*(\\(|:)").matcher(block);

		while (matcher.find()) {
			fields.add(matcher.group(1));
		}

		fields.remove(section);

		return fields;
	}

	/** Balanced-paren removal, so a wrapped argument list cannot look like a field. */
	private static String withoutArgumentLists(String block) {
		final var out = new StringBuilder();
		int depth = 0;

		for (int i = 0; i < block.length(); i++) {
			final var c = block.charAt(i);

			if (c == '(') {
				depth++;
			}
			else if (c == ')') {
				depth--;
			}
			else if (depth == 0) {
				out.append(c);
			}
		}

		return out.toString();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Cannot read " + path, e);
		}
	}

	/**
	 * Located by walking up from the working directory rather than hardcoded, because
	 * Gradle's test working directory is the module and a developer's IDE may use the repo
	 * root. A guard that only runs under one of those is not a guard.
	 */
	private static Path sourceRoot() {
		var dir = Path.of("").toAbsolutePath();

		while (dir != null) {
			for (var prefix : List.of("src", "dcb/src")) {
				final var candidate = dir.resolve(prefix).resolve("main/java/org/olf/dcb/graphql");

				if (Files.isDirectory(candidate)) {
					return candidate;
				}
			}

			dir = dir.getParent();
		}

		throw new IllegalStateException("Cannot locate org.olf.dcb.graphql sources from " + Path.of("").toAbsolutePath());
	}
}
