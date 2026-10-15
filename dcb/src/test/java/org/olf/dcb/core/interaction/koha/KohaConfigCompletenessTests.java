package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.ConfigCompleteness.acceptedHostLms;
import static org.olf.dcb.test.ConfigCompleteness.unreadable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class KohaConfigCompletenessTests {
	private static final String KOHA = "org.olf.dcb.core.interaction.koha.KohaHostLmsClient";

	// Deliberate, unlike the other adapters' gaps: a Koha used for circulation alone needs no
	// harvest keys, so HostLmsConfigValidator warns about base-url and metadata-prefix and
	// accepts the config
	private static final Set<String> KNOWN_GAPS = Set.of("KohaOaiPmhIngestSource");

	@Test
	void shouldReadEveryValueTheClientNeedsFromAConfigTheValidatorAccepts() {
		final var hostLms = acceptedHostLms(KOHA, minimalConfig());
		final var config = new KohaClientConfig(hostLms);

		final var reads = new LinkedHashMap<String, Callable<?>>();
		reads.put("getApiUrl", config::getApiUrl);
		reads.put("getClientId", config::getClientId);
		reads.put("getClientSecret", config::getClientSecret);
		reads.put("getDcbSharingLibraryCode", config::getDcbSharingLibraryCode);
		reads.put("getVirtualItemLibraryCode", config::getVirtualItemLibraryCode);
		reads.put("KohaOaiPmhIngestSource", () -> new KohaOaiPmhIngestSource(hostLms,
			null, null, null, null, null, null, null, null, null));

		final var failures = unreadable(reads);

		assertThat("Unreadable from an accepted config: " + failures, failures.keySet(), is(KNOWN_GAPS));
	}

	private static Map<String, Object> minimalConfig() {
		final var config = new LinkedHashMap<String, Object>();
		config.put("api-url", "https://koha.example.org/api/v1");
		config.put("client_id", "client");
		config.put("client_secret", "secret");
		config.put("default-agency-code", "agency");
		config.put("sharing-library-code", "SHARING");
		config.put("virtual-item-library-code", "VIRTUAL");
		return config;
	}
}
