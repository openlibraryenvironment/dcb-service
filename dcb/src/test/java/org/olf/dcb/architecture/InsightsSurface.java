package org.olf.dcb.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * InsightsController, read as text so the architecture tests can ask questions about the
 * whole surface rather than about whichever endpoint somebody remembered to cover.
 *
 * Source rather than reflection because two of the properties under test - which parameters
 * an endpoint declares optional, and which repository call each one flows into - are erased
 * by the time there is a bean to reflect on.
 */
final class InsightsSurface {

	private static final String CONTROLLER =
		"org/olf/dcb/core/api/InsightsController.java";

	// Every mapping in the controller, because the class boundary IS the surface. A literal
	// rather than a regex: escaping it twice helps nobody read it.
	private static final String STATS_MARKER = "@Get(\"";

	private InsightsSurface() {}

	record Endpoint(String path, String signature, String body) {}

	static List<Endpoint> endpoints() throws IOException {
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

	static List<Endpoint> endpointsQuietly() {
		try {
			return endpoints();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read " + CONTROLLER, e);
		}
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

	private static Path sourceRoot() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var moduleSourceRoot = workingDirectory.resolve("src/main/java");

		if (Files.exists(moduleSourceRoot)) {
			return moduleSourceRoot;
		}

		return workingDirectory.resolve("dcb/src/main/java");
	}
}
