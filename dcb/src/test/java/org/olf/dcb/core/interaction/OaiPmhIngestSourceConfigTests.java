package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;

/**
 * The client config an OAI harvest cannot be constructed without.
 * <p>
 * A Koha created through the admin UI has neither key - the form never asked for
 * them - so every one of them reported "Ingest Check Failed: No value present" and
 * then quietly never harvested. Neither message named the Host LMS or the key.
 */
class OaiPmhIngestSourceConfigTests {
	@Test
	void shouldNameTheHostLmsAndTheKeyWhenTheBaseUrlIsMissing() {
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> OaiPmhIngestSource.requiredClientConfig(hostLms(Map.of(
				"metadata-prefix", "marcxml")), "base-url"));

		assertThat(exception.getMessage(), containsString("some-koha"));
		assertThat(exception.getMessage(), containsString("base-url"));
	}

	@Test
	void shouldNameTheHostLmsAndTheKeyWhenTheMetadataPrefixIsMissing() {
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> OaiPmhIngestSource.requiredClientConfig(hostLms(Map.of(
				"base-url", "https://catalogue.example.org")), "metadata-prefix"));

		assertThat(exception.getMessage(), containsString("some-koha"));
		assertThat(exception.getMessage(), containsString("metadata-prefix"));
	}

	@Test
	void shouldTreatABlankValueAsMissing() {
		// The guided form drops empty fields, but a config hand-edited in the JSON
		// editor can carry "" - which UriBuilder accepts and then harvests nothing from
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> OaiPmhIngestSource.requiredClientConfig(hostLms(Map.of(
				"base-url", "   ")), "base-url"));

		assertThat(exception.getMessage(), containsString("base-url"));
	}

	@Test
	void shouldTolerateAHostLmsWithNothingInItsClientConfig() {
		final var withoutConfig = DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("some-koha")
			.name("Some Koha")
			.lmsClientClass("org.olf.dcb.core.interaction.koha.KohaHostLmsClient")
			.build();

		final var exception = assertThrows(IllegalArgumentException.class,
			() -> OaiPmhIngestSource.requiredClientConfig(withoutConfig, "base-url"));

		assertThat(exception.getMessage(), containsString("base-url"));
	}

	@Test
	void shouldReturnAConfiguredValue() {
		assertThat(OaiPmhIngestSource.requiredClientConfig(
				hostLms(Map.of("base-url", "https://catalogue.example.org")), "base-url"),
			is("https://catalogue.example.org"));
	}

	private DataHostLms hostLms(Map<String, Object> clientConfig) {
		return DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("some-koha")
			.name("Some Koha")
			.lmsClientClass("org.olf.dcb.core.interaction.koha.KohaHostLmsClient")
			.clientConfig(new HashMap<>(clientConfig))
			.build();
	}
}
