package org.olf.dcb.devtools.interaction.dummy.responder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * FileResponseProvider is a utility class for loading and serving mock LMS responses
 * based on a combination of `state` and `role`.
 *
 * It reads a single JSON file (`dummy-responses.json`) from the classpath and caches the
 * results for fast lookup. This allows test or dummy implementations to be driven
 * by easily editable file-based data.
 *
 * Expected JSON file format:
 * {
 *   "pickup_transit-supplier": {
 *     "itemStatus": "ITEM_TRANSIT",
 *     "requestStatus": "CONFIRMED",
 *     ...
 *   },
 *   ...
 * }
 *
 * Each key is a composite of `state-role`, and the value is a simple response map.
 */
@Slf4j
@Singleton
class FileResponseProvider {

	private static final String RESPONSE_FILE = "/dummy-responses.json";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static Map<String, Map<String, Object>> cachedResponses;

	static {
		try (InputStream inputStream = FileResponseProvider.class.getResourceAsStream(RESPONSE_FILE)) {
			if (inputStream == null) {
				throw new IOException("Response file not found: " + RESPONSE_FILE);
			}
			cachedResponses = OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {});
			log.info("Loaded {} dummy responses from file", cachedResponses.size());
		} catch (IOException e) {
			log.error("Failed to load dummy responses from file", e);
			cachedResponses = Map.of(); // empty fallback
		}
	}

	static DummyResponse getResponse(String state, String role) {
		String key = buildKey(state, role);

		log.debug("Loading response for key: {}", key);

		Map<String, Object> data = cachedResponses.get(key);

		if (data == null) {
			log.warn("No file-based response found for key: {}", key);
			data = Map.of("itemStatus", "UNKNOWN", "requestStatus", "UNKNOWN");
		}

		return DummyResponse.builder().data(data).build();
	}

	private static String buildKey(String state, String role) {
		return state.toLowerCase() + "-" + role.toLowerCase();
	}
}
