package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.PatronRequestRepository;

/**
 * An Insights route that makes a parameter optional must call repository methods that can
 * take the null it will hand them. Micronaut Data refuses to bind a null to a parameter that
 * is not {@code @Nullable}, before the statement reaches Postgres, so an IS NULL branch in
 * the SQL is no defence and the 500 is unconditional. Why it is a test: docs/insights.md 3.2a.
 */
class InsightsNullableParameterArchitectureTests {

	/**
	 * The locals an endpoint body passes on that can hold null. libraryCode is whatever
	 * StatsScopeGuard resolved, so it is null exactly when the route let the caller omit
	 * the code; codes is /turnaround's name for the same thing.
	 */
	private static final Set<String> LIBRARY_ARGUMENTS =
		Set.of("libraryCode", "codes", "scope.libraryCode()");

	/**
	 * A floor, so a parser that quietly stops matching cannot pass as a clean run. Set just
	 * under the 45 call sites present when this was written: a floor of 30 was already met
	 * by a parser that was missing every wrapped call site, this test's own first bug.
	 */
	private static final int KNOWN_MINIMUM_CALLS = 40;

	@Test
	void everyOptionalRouteParameterReachesAQueryParameterThatAcceptsNull()
		throws IOException {

		final var offenders = new ArrayList<String>();

		for (final var endpoint : InsightsSurface.endpoints()) {
			final var optional = optionalArgumentsOf(endpoint.signature());

			for (final var call : repositoryCalls(endpoint.body())) {
				final var method = resolve(call);
				final var parameters = method.getParameters();

				for (var i = 0; i < call.arguments().size() && i < parameters.length; i++) {
					if (optional.contains(call.arguments().get(i))
						&& !acceptsNull(parameters[i])) {

						offenders.add(endpoint.path() + " passes optional "
							+ call.arguments().get(i) + " to " + method.getName()
							+ " parameter " + i + ", which is not @Nullable");
					}
				}
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Micronaut Data throws IllegalArgumentException before the query runs, "
				+ "so each of these is a 500 for any caller who omits the parameter: "
				+ offenders);
	}

	@Test
	void theCallsAreActuallyBeingRead() throws IOException {
		var found = 0;

		for (final var endpoint : InsightsSurface.endpoints()) {
			found += repositoryCalls(endpoint.body()).size();
		}

		final var total = found;

		assertTrue(total >= KNOWN_MINIMUM_CALLS,
			() -> "Found " + total + " repository calls in the Insights controller, "
				+ "expected at least " + KNOWN_MINIMUM_CALLS + " - the test above is "
				+ "no longer reading the surface it claims to");
	}

	/** The argument names this endpoint can hand a repository as null. */
	private static Set<String> optionalArgumentsOf(String signature) {
		final var optional = new java.util.HashSet<String>();

		// No @NotNull on the code means the caller may omit it, and a consortium-level
		// caller who does is handed StatsScope.unscoped() - a null library code.
		if (!signature.contains("@NotNull @QueryValue String requestedLibraryCode")) {
			optional.addAll(LIBRARY_ARGUMENTS);
		}

		if (signature.contains("@Nullable @QueryValue Instant startDate")) {
			optional.add("startDate");
		}

		if (signature.contains("@Nullable @QueryValue Instant endDate")) {
			optional.add("endDate");
		}

		return optional;
	}

	private record RepositoryCall(Class<?> repository, String name, List<String> arguments) {}

	private static List<RepositoryCall> repositoryCalls(String body) {
		final var calls = new ArrayList<RepositoryCall>();

		calls.addAll(callsTo(body, "patronRequestRepository", PatronRequestRepository.class));
		calls.addAll(callsTo(body, "clusterRecordRepository", ClusterRecordRepository.class));

		return calls;
	}

	/**
	 * The receiver name alone, then the dot: half these call sites wrap the line between the
	 * two, and matching "patronRequestRepository." skipped every one of them - including both
	 * dashboard-metrics partner queries, which is the defect this test exists for.
	 */
	private static List<RepositoryCall> callsTo(String body, String receiver, Class<?> type) {
		final var calls = new ArrayList<RepositoryCall>();
		var from = body.indexOf(receiver);

		while (from >= 0) {
			final var dot = skipWhitespace(body, from + receiver.length());

			if (dot >= body.length() || body.charAt(dot) != '.') {
				from = body.indexOf(receiver, from + receiver.length());
				continue;
			}

			final var openParen = body.indexOf('(', dot);
			final var name = body.substring(dot + 1, openParen).trim();
			final var closeParen = matchingParen(body, openParen);

			calls.add(new RepositoryCall(type, name,
				splitArguments(body.substring(openParen + 1, closeParen))));

			from = body.indexOf(receiver, closeParen);
		}

		return calls;
	}

	private static int skipWhitespace(String body, int from) {
		var at = from;

		while (at < body.length() && Character.isWhitespace(body.charAt(at))) {
			at++;
		}

		return at;
	}

	private static int matchingParen(String body, int openParen) {
		var depth = 0;

		for (var i = openParen; i < body.length(); i++) {
			if (body.charAt(i) == '(') depth++;
			if (body.charAt(i) == ')') {
				depth--;
				if (depth == 0) return i;
			}
		}

		return body.length() - 1;
	}

	/** Top-level commas only, so a nested call stays one argument. */
	private static List<String> splitArguments(String arguments) {
		final var split = new ArrayList<String>();
		final var current = new StringBuilder();
		var depth = 0;

		for (var i = 0; i < arguments.length(); i++) {
			final var c = arguments.charAt(i);

			if (c == '(' || c == '<') depth++;
			if (c == ')' || c == '>') depth--;

			if (c == ',' && depth == 0) {
				split.add(current.toString().trim());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}

		if (!current.toString().isBlank()) {
			split.add(current.toString().trim());
		}

		return split;
	}

	/**
	 * Overloads are separated by argument count - findTopRequestors is the only pair, and
	 * they differ by one. A name the interface does not carry is a parser fault, not a
	 * finding, so it fails loudly rather than being skipped.
	 */
	private static Method resolve(RepositoryCall call) {
		for (final var method : call.repository().getMethods()) {
			if (method.getName().equals(call.name())
				&& method.getParameterCount() == call.arguments().size()) {

				return method;
			}
		}

		throw new IllegalStateException(call.repository().getSimpleName() + " has no "
			+ call.name() + " taking " + call.arguments().size() + " arguments");
	}

	/** By simple name: Micronaut, Jakarta and JSpecify all spell it @Nullable. */
	private static boolean acceptsNull(java.lang.reflect.Parameter parameter) {
		for (final var annotation : parameter.getAnnotations()) {
			if (annotation.annotationType().getSimpleName().equals("Nullable")) {
				return true;
			}
		}

		return false;
	}
}
