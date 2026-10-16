package org.olf.dcb.indexing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.indexing.SharedIndexConfiguration.LATEST_INDEX_VERSION;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A rebuilt index is created from fullSettings-N.json (docs/shared-index.md), so it must say what
 * settings-N.json and mappings-N.json say. Regenerate it with
 * ./gradlew :dcb:generateSharedIndexFullSettings.
 */
class SharedIndexFullSettingsTests {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void shouldCreateARebuiltIndexWithTheCurrentSettings() throws IOException {
		assertThat(fullSettings().get("settings"), is(read("settings-%d.json")));
	}

	@Test
	void shouldCreateARebuiltIndexWithTheCurrentMappings() throws IOException {
		assertThat(fullSettings().get("mappings"), is(read("mappings-%d.json")));
	}

	private JsonNode fullSettings() throws IOException {
		return read("fullSettings-%d.json");
	}

	private JsonNode read(String name) throws IOException {
		final var resource = "sharedIndex/" + name.formatted(LATEST_INDEX_VERSION);

		try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing test resource " + resource);
			}
			return mapper.readTree(in);
		}
	}
}
