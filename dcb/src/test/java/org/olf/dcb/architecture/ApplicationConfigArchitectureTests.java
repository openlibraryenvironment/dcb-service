package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A key declared twice in application.yml silently deletes the first one.
 *
 * <h2>Why this test exists</h2>
 *
 * {@code dcb.security.admin-ui} was added at the top of the {@code dcb:} block. A
 * {@code dcb.security:} key already existed 120 lines below it. YAML keeps the LAST of a
 * duplicate pair, so the new block was discarded at parse time - and the result was an
 * access control that read correctly, tested correctly, deployed correctly, and configured
 * nothing. It reported itself OFF while its environment variable was plainly set.
 *
 * Nothing failed. No parser complained, no startup warning appeared, and the only symptom
 * was a security check quietly not running. That is the worst shape a defect can take, and
 * it is exactly the shape a structural test catches for free.
 *
 * <h2>Scope</h2>
 *
 * Deliberately only the top two levels - the roots and their immediate children. That is
 * where this class of mistake actually happens, because those are the blocks far enough
 * apart in the file that nobody sees both at once. Going deeper would mean a real YAML
 * parse and a lot more machinery for a case that has not bitten anybody.
 */
class ApplicationConfigArchitectureTests {

	private static final List<String> CONFIGS = List.of(
		"application.yml", "application-test.yml");

	/** A top-level key: no leading whitespace, ends in a colon. */
	private static final Pattern ROOT_KEY = Pattern.compile("^([A-Za-z][\\w.-]*):\\s*$");

	/** A second-level key: exactly two spaces of indent. */
	private static final Pattern CHILD_KEY = Pattern.compile("^ {2}([A-Za-z][\\w.-]*):\\s*$");

	@Test
	@DisplayName("No top-level key is declared twice")
	void noDuplicateRootKeys() throws IOException {
		for (final var config : existingConfigs()) {
			final var seen = new HashSet<String>();
			final var duplicates = new ArrayList<String>();

			for (final var line : Files.readAllLines(config)) {
				final var matcher = ROOT_KEY.matcher(line);

				if (matcher.matches() && !seen.add(matcher.group(1))) {
					duplicates.add(matcher.group(1));
				}
			}

			assertTrue(duplicates.isEmpty(),
				() -> config.getFileName() + " declares these top-level keys twice, so the "
					+ "FIRST of each is silently discarded: " + duplicates);
		}
	}

	@Test
	@DisplayName("No second-level key is declared twice under the same parent")
	void noDuplicateChildKeys() throws IOException {
		for (final var config : existingConfigs()) {
			final var duplicates = new ArrayList<String>();

			var parent = "";
			var seen = new HashSet<String>();

			for (final var line : Files.readAllLines(config)) {
				final var root = ROOT_KEY.matcher(line);

				if (root.matches()) {
					parent = root.group(1);
					seen = new HashSet<>();
					continue;
				}

				final var child = CHILD_KEY.matcher(line);

				if (child.matches() && !seen.add(child.group(1))) {
					duplicates.add(parent + "." + child.group(1));
				}
			}

			assertTrue(duplicates.isEmpty(),
				() -> config.getFileName() + " declares these keys twice under the same "
					+ "parent, so the FIRST of each is silently discarded - which is how "
					+ "dcb.security.admin-ui once existed while configuring nothing: "
					+ duplicates);
		}
	}

	@Test
	@DisplayName("The file being checked is actually there")
	void theConfigIsFound() throws IOException {
		// Guard the guard: a moved or renamed application.yml would make both assertions
		// above pass over an empty list.
		assertTrue(!existingConfigs().isEmpty(),
			"No application config found - has it moved?");
	}

	private static List<Path> existingConfigs() {
		final var found = new ArrayList<Path>();

		for (final var name : CONFIGS) {
			final var fromModuleRoot = Paths.get("src/main/resources").resolve(name);
			final var fromRepoRoot = Paths.get("dcb/src/main/resources").resolve(name);

			if (Files.exists(fromModuleRoot)) {
				found.add(fromModuleRoot);
			}
			else if (Files.exists(fromRepoRoot)) {
				found.add(fromRepoRoot);
			}
		}

		return found;
	}
}
