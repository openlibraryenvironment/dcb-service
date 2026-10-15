package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.ConfigCompleteness.acceptedHostLms;
import static org.olf.dcb.test.ConfigCompleteness.unreadable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class AlmaConfigCompletenessTests {
	private static final String ALMA = "org.olf.dcb.core.interaction.alma.AlmaHostLmsClient";

	// Read at run time and accepted absent by HostLmsConfigValidator. Remove an entry when the
	// validator starts requiring its key; a stale entry fails this test as surely as a new gap.
	// The harvest needs metadata-prefix, ingest is on unless switched off, and nothing warns
	private static final Set<String> KNOWN_GAPS = Set.of("AlmaOaiPmhIngestSource");

	@Test
	void shouldReadEveryValueTheClientNeedsFromAConfigTheValidatorAccepts() {
		final var hostLms = acceptedHostLms(ALMA, minimalConfig());
		final var config = new AlmaClientConfig(hostLms);

		final var reads = new LinkedHashMap<String, Callable<?>>();
		reads.put("getBaseUrl", config::getBaseUrl);
		reads.put("getApiKey", config::getApiKey);
		reads.put("getDcbSharingLibraryCode", config::getDcbSharingLibraryCode);
		reads.put("getVirtualItemLibraryCode", config::getVirtualItemLibraryCode);
		reads.put("getVirtualItemLocationCode", config::getVirtualItemLocationCode);
		reads.put("getRequestCancellationReason", config::getRequestCancellationReason);
		reads.put("AlmaOaiPmhIngestSource", () -> new AlmaOaiPmhIngestSource(hostLms,
			null, null, null, null, null, null, null, null, null));

		final var failures = unreadable(reads);

		assertThat("Unreadable from an accepted config: " + failures, failures.keySet(), is(KNOWN_GAPS));
	}

	private static Map<String, Object> minimalConfig() {
		final var config = new LinkedHashMap<String, Object>();
		config.put("base-url", "https://example.alma.exlibrisgroup.com");
		config.put("alma-url", "https://api-eu.hosted.exlibrisgroup.com");
		config.put("apikey", "apikey");
		config.put("institution-code", "01EXAMPLE_INST");
		config.put("default-agency-code", "agency");
		config.put("sharing-library-code", "SHARING");
		config.put("virtual-item-library-code", "VIRTUAL");
		config.put("virtual-item-location-code", "VIRTUAL_LOC");
		config.put("request-cancellation-reason", "OPENRS");
		return config;
	}
}
