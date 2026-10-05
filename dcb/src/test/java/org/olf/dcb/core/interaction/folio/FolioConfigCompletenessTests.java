package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.ConfigCompleteness.acceptedHostLms;
import static org.olf.dcb.test.ConfigCompleteness.unreadable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class FolioConfigCompletenessTests {
	private static final String FOLIO = "org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient";

	// Read at run time and accepted absent by HostLmsConfigValidator. Remove an entry when the
	// validator starts requiring its key; a stale entry fails this test as surely as a new gap.
	// The harvest needs record-syntax and metadata-prefix, and ingest is on unless switched off
	private static final Set<String> KNOWN_GAPS = Set.of("FolioOaiPmhIngestSource");

	@Test
	void shouldReadEveryValueTheClientNeedsFromAConfigTheValidatorAccepts() {
		final var hostLms = acceptedHostLms(FOLIO, minimalConfig());

		final var reads = new LinkedHashMap<String, Callable<?>>();
		reads.put("FolioOaiPmhIngestSource", () -> new FolioOaiPmhIngestSource(hostLms,
			null, null, null, null, null, null, null, null, null));

		final var failures = unreadable(reads);

		assertThat("Unreadable from an accepted config: " + failures, failures.keySet(), is(KNOWN_GAPS));
	}

	private static Map<String, Object> minimalConfig() {
		final var config = new LinkedHashMap<String, Object>();
		config.put("base-url", "https://folio.example.org");
		config.put("apikey", "apikey");
		config.put("default-agency-code", "agency");
		return config;
	}
}
