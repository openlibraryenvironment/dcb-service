package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class Iso18626ArchitectureTests {
	private static final String ISO_PACKAGE_IMPORT = "import org.olf.dcb.request.lifecycle.iso18626";
	private static final List<String> IMPERATIVE_BOUNDARY_PACKAGES = List.of(
		"org/olf/dcb/request/workflow",
		"org/olf/dcb/request/fulfilment",
		"org/olf/dcb/tracking",
		"org/olf/dcb/core/model",
		"org/olf/dcb/core/interaction");

	@Test
	void existingImperativeBoundariesDoNotImportIso18626Package()
		throws IOException {

		final var offenders = new ArrayList<Path>();

		for (final var packagePath : IMPERATIVE_BOUNDARY_PACKAGES) {
			for (final var sourceFile : sourceFilesUnder(sourceRoot().resolve(packagePath))) {
				final var source = Files.readString(sourceFile);

				if (source.contains(ISO_PACKAGE_IMPORT)) {
					offenders.add(sourceRoot().relativize(sourceFile));
				}
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "ISO18626 package imports must not leak into existing imperative boundaries: " + offenders);
	}

	@Test
	void lifecycleCodeOutsideIso18626PackageDoesNotImportIso18626Package()
		throws IOException {

		final var offenders = new ArrayList<Path>();

		for (final var sourceFile : sourceFilesUnder(sourceRoot().resolve("org/olf/dcb/request/lifecycle"))) {
			if (isInsideIso18626Package(sourceFile)) {
				continue;
			}

			final var source = Files.readString(sourceFile);

			if (source.contains(ISO_PACKAGE_IMPORT)) {
				offenders.add(sourceRoot().relativize(sourceFile));
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "Lifecycle package must only depend on ISO18626 through explicit activation seams: " + offenders);
	}

	@Test
	void iso18626NamedProductionCodeStaysInsideIso18626Package()
		throws IOException {

		final var offenders = new ArrayList<Path>();

		for (final var sourceFile : sourceFilesUnder(sourceRoot())) {
			if (isInsideIso18626Package(sourceFile)) {
				continue;
			}

			final var source = Files.readString(sourceFile);

			if (source.contains("Iso18626") || source.contains("ISO18626")) {
				offenders.add(sourceRoot().relativize(sourceFile));
			}
		}

		assertTrue(offenders.isEmpty(),
			() -> "ISO18626-specific production code must stay inside request/lifecycle/iso18626: " + offenders);
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

	private static boolean isInsideIso18626Package(Path path) {
		return normalise(path).contains("org/olf/dcb/request/lifecycle/iso18626/");
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
}
