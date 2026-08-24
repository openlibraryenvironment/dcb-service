package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class DatabaseBrandAssetStoreNativeImageTests {
	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void generatedCacheImplementationsAreRegisteredForNativeReflection() throws Exception {
		final var implementation = DatabaseBrandAssetStore.createServedCache()
			.asMap().getClass().getName();

		try (var input = getClass().getResourceAsStream(
			"/META-INF/native-image/org.olf.dcb/dcb/reflect-config.json")) {
			final List<Map<String, Object>> entries = JSON.readValue(
				new String(input.readAllBytes(), StandardCharsets.UTF_8),
				new TypeReference<>() { });

			assertThat(entries, hasItem(Map.of(
				"name", implementation,
				"methods", List.of(Map.of(
					"name", "<init>",
					"parameterTypes", List.of(
						"com.github.benmanes.caffeine.cache.Caffeine",
						"com.github.benmanes.caffeine.cache.AsyncCacheLoader",
						"boolean"))))));

			assertThat(entries, hasItem(Map.of(
				"name", "com.github.benmanes.caffeine.cache.PSAMW",
				"methods", List.of(Map.of(
					"name", "<init>",
					"parameterTypes", List.of())))));
		}
	}
}
