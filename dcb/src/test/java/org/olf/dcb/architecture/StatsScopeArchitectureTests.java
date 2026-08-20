package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The guard on the guard: a new Insights endpoint that does not route its library filter
 * through StatsScopeGuard fails the build.
 *
 * Scoping is a property of the whole surface, not of any single endpoint - one added later
 * without it reopens the cross-tenant read for all of them, and prose cannot enforce that.
 */
class StatsScopeArchitectureTests {

	private static final String CONTROLLER =
		"org/olf/dcb/core/api/InsightsController.java";

	// Every mapping in the controller, because the class boundary IS the surface. A literal
	// rather than a regex: escaping it twice helps nobody read it.
	private static final String STATS_MARKER = "@Get(\"";

	/** A floor, not an exact count - exact trains everyone to bump a number without reading. */
	private static final int KNOWN_MINIMUM_ENDPOINTS = 30;

	@Test
	void everyStatsEndpointTakesTheAuthenticationItMustScopeOn() throws IOException {
		final var offenders = new ArrayList<String>();

		for (final var endpoint : statsEndpoints()) {
			if (!endpoint.signature().contains("Authentication authentication")) {
				offenders.add(endpoint.path()
					+ " does not take Authentication, so it cannot know who is asking");
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Statistics endpoints must derive their library filter from the "
				+ "caller's token, not from the query parameter alone: " + offenders);
	}

	@Test
	void everyStatsEndpointRoutesItsLibraryFilterThroughTheGuard() throws IOException {
		final var offenders = new ArrayList<String>();

		for (final var endpoint : statsEndpoints()) {
			if (!endpoint.body().contains("statsScopeGuard.resolve(")) {
				offenders.add(endpoint.path() + " never calls statsScopeGuard.resolve");
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "A statistics endpoint that skips the guard answers for whatever "
				+ "library the caller names: " + offenders);
	}

	@Test
	void noStatsEndpointStillBindsTheRawLibraryCodeParameter() throws IOException {
		final var offenders = new ArrayList<String>();

		for (final var endpoint : statsEndpoints()) {
			// The scoped shape renames the parameter to requestedLibraryCode and rebinds
			// libraryCode from the guard's answer inside the lambda. A method still
			// binding `String libraryCode` straight off the query string has been added
			// or reverted to the unscoped shape.
			if (bindsRawLibraryCode(endpoint.signature())) {
				offenders.add(endpoint.path() + " binds libraryCode directly");
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "libraryCode must arrive via StatsScopeGuard, never straight from the "
				+ "query string: " + offenders);
	}

	@Test
	void theSurfaceIsNotEmpty() {
		// Guard the guard: a rename of the controller or the annotation would make
		// every assertion above pass over an empty list.
		final var found = statsEndpointsQuietly().size();

		assertTrue(found >= KNOWN_MINIMUM_ENDPOINTS,
			() -> "Found " + found + " Insights endpoints, expected at least "
				+ KNOWN_MINIMUM_ENDPOINTS + " - this test is no longer looking at "
				+ "the whole surface");
	}

	/**
	 * Exact parameter name, not a prefix: /turnaround legitimately takes `libraryCodes`
	 * (plural) and collapses it inside the guard. A substring match reports that as an
	 * offender and trains everyone to ignore this test.
	 */
	private static boolean bindsRawLibraryCode(String signature) {
		final var marker = "@QueryValue String libraryCode";
		final var at = signature.indexOf(marker);

		if (at < 0) {
			return false;
		}

		final var next = at + marker.length();

		return next >= signature.length()
			|| !Character.isJavaIdentifierPart(signature.charAt(next));
	}

	private record Endpoint(String path, String signature, String body) {}

	private static List<Endpoint> statsEndpoints() throws IOException {
		final var source = Files.readString(sourceRoot().resolve(CONTROLLER));
		final var endpoints = new ArrayList<Endpoint>();
		var from = source.indexOf(STATS_MARKER);

		while (from >= 0) {
			final var pathEnd = source.indexOf(')', from);
			final var path = source.substring(from, pathEnd + 1);
			final var openParen = source.indexOf('(', source.indexOf("public", pathEnd));
			final var signatureEnd = source.indexOf('{', openParen);

			endpoints.add(new Endpoint(path,
				source.substring(openParen, signatureEnd),
				bodyFrom(source, signatureEnd)));

			from = source.indexOf(STATS_MARKER, pathEnd);
		}

		return endpoints;
	}

	/** The method body, by brace matching from its opening brace. */
	private static String bodyFrom(String source, int openBrace) {
		var depth = 0;

		for (var i = openBrace; i < source.length(); i++) {
			final var c = source.charAt(i);

			if (c == '{') depth++;
			if (c == '}') {
				depth--;
				if (depth == 0) return source.substring(openBrace, i + 1);
			}
		}

		return source.substring(openBrace);
	}

	private static List<Endpoint> statsEndpointsQuietly() {
		try {
			return statsEndpoints();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read " + CONTROLLER, e);
		}
	}

	private static Path sourceRoot() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var moduleSourceRoot = workingDirectory.resolve("src/main/java");

		if (Files.exists(moduleSourceRoot)) {
			return moduleSourceRoot;
		}

		return workingDirectory.resolve("dcb/src/main/java");
	}
}
