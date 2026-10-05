package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SierraApiVersionTests {
	@Test
	void shouldUseVersionSixUnlessTold() {
		assertThat(HostLmsSierraApiClient.rootUriFor(Map.of("base-url", "https://sierra.example.org")),
			is(URI.create("https://sierra.example.org/iii/sierra-api/v6/")));
	}

	@Test
	void shouldUseTheConfiguredVersion() {
		assertThat(HostLmsSierraApiClient.rootUriFor(
				Map.of("base-url", "https://sierra.example.org", "api-version", "v5")),
			is(URI.create("https://sierra.example.org/iii/sierra-api/v5/")));
	}

	@Test
	void shouldRefuseAVersionSierraDoesNotServe() {
		assertThrows(IllegalArgumentException.class, () -> HostLmsSierraApiClient.rootUriFor(
			Map.of("base-url", "https://sierra.example.org", "api-version", "6")));
	}
}
