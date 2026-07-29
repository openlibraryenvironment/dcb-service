package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ProtocolAdapterArchitectureTests {
	private static final List<ProtocolAdapterBoundary> PROTOCOL_BOUNDARIES
		= List.of(
			new ProtocolAdapterBoundary(
				"NCIP",
				"ncip",
				List.of("Ncip", "NCIP")));

	private static final List<String> IMPERATIVE_BOUNDARY_PACKAGES = List.of(
		"org/olf/dcb/request/workflow",
		"org/olf/dcb/request/fulfilment",
		"org/olf/dcb/tracking",
		"org/olf/dcb/core/model",
		"org/olf/dcb/core/interaction");

	@Test
	void existingImperativeBoundariesDoNotImportProtocolAdapters()
		throws IOException {

		final var offenders = new ArrayList<String>();

		for (final var protocol : PROTOCOL_BOUNDARIES) {
			for (final var packagePath : IMPERATIVE_BOUNDARY_PACKAGES) {
				for (final var sourceFile : sourceFilesUnder(sourceRoot().resolve(packagePath))) {
					final var source = Files.readString(sourceFile);

					if (importsProtocolAdapter(source, protocol)) {
						offenders.add(protocol.name() + ": "
							+ sourceRoot().relativize(sourceFile));
					}
				}
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Protocol adapter imports must not leak into existing imperative boundaries: "
				+ offenders);
	}

	@Test
	void lifecycleCodeOutsideProtocolAdapterPackagesDoesNotImportAdapters()
		throws IOException {

		final var offenders = new ArrayList<String>();

		for (final var protocol : PROTOCOL_BOUNDARIES) {
			for (final var sourceFile : sourceFilesUnder(
				sourceRoot().resolve("org/olf/dcb/request/lifecycle"))) {

				if (isInsideProtocolPackage(sourceFile, protocol)) {
					continue;
				}

				final var source = Files.readString(sourceFile);

				if (importsProtocolAdapter(source, protocol)) {
					offenders.add(protocol.name() + ": "
						+ sourceRoot().relativize(sourceFile));
				}
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Lifecycle abstractions must not depend on protocol adapter packages: "
				+ offenders);
	}

	@Test
	void protocolNamedProductionCodeStaysInsideProtocolAdapterPackages()
		throws IOException {

		final var offenders = new ArrayList<String>();

		for (final var protocol : PROTOCOL_BOUNDARIES) {
			for (final var sourceFile : sourceFilesUnder(sourceRoot())) {
				if (isInsideProtocolPackage(sourceFile, protocol)) {
					continue;
				}

				final var source = Files.readString(sourceFile);

				if (namesProtocolAdapter(source, protocol)) {
					offenders.add(protocol.name() + ": "
						+ sourceRoot().relativize(sourceFile));
				}
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Protocol-specific production code must stay inside its adapter package: "
				+ offenders);
	}

	private static boolean importsProtocolAdapter(
		String source,
		ProtocolAdapterBoundary protocol) {

		return source.contains("import " + protocol.packageName());
	}

	/**
	 * A file "names" a protocol when it <em>declares</em> a protocol-named type -
	 * not merely when the token appears somewhere in it. Matching raw content also
	 * flagged shared command objects whose only mention of NCIP was a comment,
	 * which says nothing about where protocol code lives.
	 */
	private static boolean namesProtocolAdapter(
		String source,
		ProtocolAdapterBoundary protocol) {

		return protocol.nameTokens().stream()
			.map(token -> Pattern.compile(
				"\\b(?:class|interface|record|enum)\\s+\\w*" + Pattern.quote(token) + "\\w*"))
			.anyMatch(pattern -> pattern.matcher(source).find());
	}

	private static List<Path> sourceFilesUnder(Path root) throws IOException {
		if (!Files.exists(root)) {
			return List.of();
		}

		try (Stream<Path> paths = Files.walk(root)) {
			return paths
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.toList();
		}
	}

	private static boolean isInsideProtocolPackage(
		Path path,
		ProtocolAdapterBoundary protocol) {

		final var normalised = normalise(path);

		return protocol.packagePaths().stream().anyMatch(normalised::contains);
	}

	private static String normalise(Path path) {
		return path.toAbsolutePath().normalize().toString().replace('\\', '/');
	}

	private static Path sourceRoot() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var moduleSourceRoot = workingDirectory.resolve("src/main/java");

		if (Files.exists(moduleSourceRoot)) {
			return moduleSourceRoot;
		}

		final var repositorySourceRoot = workingDirectory.resolve("dcb/src/main/java");

		if (Files.exists(repositorySourceRoot)) {
			return repositorySourceRoot;
		}

		throw new IllegalStateException(
			"Could not find production source root from " + workingDirectory);
	}

	private record ProtocolAdapterBoundary(
		String name,
		String packageSegment,
		List<String> nameTokens) {

		String packageName() {
			return "org.olf.dcb.request.lifecycle." + packageSegment();
		}

		/**
		 * Every package a protocol may legitimately be named in. Under the unified
		 * host-interaction model NCIP has three homes, not one:
		 *   - request/lifecycle/ncip   the declarative leaf (removable)
		 *   - core/interaction/ncip    shared protocol mechanics (constants + XSD)
		 *   - core/interaction/foundation  the imperative NCIP adaptor
		 * The rule that matters is that protocol code stays in one of these, and
		 * that the imperative boundaries never import the declarative leaf - which
		 * the other two tests in this class enforce.
		 */
		List<String> packagePaths() {
			return List.of(
				"org/olf/dcb/request/lifecycle/" + packageSegment() + "/",
				"org/olf/dcb/core/interaction/" + packageSegment() + "/",
				"org/olf/dcb/core/interaction/foundation/");
		}
	}
}
