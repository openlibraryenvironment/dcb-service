package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.alma.AlmaClientConfig;
import org.olf.dcb.core.interaction.koha.KohaClientConfig;
import org.olf.dcb.core.interaction.koha.KohaOaiPmhIngestSource;
import org.olf.dcb.core.interaction.polaris.PolarisConfig;
import org.olf.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.ingest.IngestSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * The defaults each adapter applies to an absent setting, against docs/host-lms-defaults.json.
 * DCB Admin keeps a copy of that file and shows these values, so a default changed here
 * without the file goes red here, and a file changed without DCB Admin's copy goes red there.
 */
class HostLmsDefaultsSnapshotTests {
	@Test
	void shouldMatchTheCommittedSnapshot() throws Exception {
		final var polaris = PolarisConfig.builder().build();

		final var fromCode = Map.of(
			"org.olf.dcb.core.interaction.alma.AlmaHostLmsClient", new TreeMap<>(Map.of(
				"default-circ-desk-code", AlmaClientConfig.DEFAULT_CIRC_DESK,
				"item-policy", AlmaClientConfig.DEFAULT_ITEM_POLICY,
				"no-renew-item-policy", AlmaClientConfig.DEFAULT_NO_RENEW_ITEM_POLICY,
				"pickup-circ-desk", AlmaClientConfig.DEFAULT_CIRC_DESK,
				"user-identifier", AlmaClientConfig.DEFAULT_USER_IDENTIFIER)),
			"org.olf.dcb.core.interaction.koha.KohaHostLmsClient", new TreeMap<>(Map.of(
				"no-renew-collection-code", KohaClientConfig.DEFAULT_NO_RENEW_COLLECTION_CODE,
				"oai-path", KohaOaiPmhIngestSource.DEFAULT_OAI_PATH,
				"virtual-patron-category", KohaClientConfig.DEFAULT_VIRTUAL_PATRON_CATEGORY)),
			"org.olf.dcb.core.interaction.polaris.PolarisLmsClient", new TreeMap<>(Map.ofEntries(
				Map.entry("enableDoubleDelete", String.valueOf(polaris.isDoubleDeleteEnabled())),
				Map.entry("harvest-watermark-overlap-minutes",
					String.valueOf(polaris.getHarvestWatermarkOverlap().toMinutes())),
				Map.entry("hold-fetching-delay", String.valueOf(PolarisConfig.DEFAULT_HOLD_FETCHING_DELAY)),
				Map.entry("hold-fetching-max-retry", String.valueOf(PolarisConfig.DEFAULT_HOLD_FETCHING_MAX_RETRY)),
				Map.entry("papi.app-id", polaris.getPapiAppId()),
				Map.entry("papi.lang-id", polaris.getPapiLangId()),
				Map.entry("papi.org-id", polaris.getPapiOrgId()),
				Map.entry("papi.papi-version", polaris.getPapiVersion()),
				Map.entry("services.language", polaris.getServicesLanguage()),
				Map.entry("services.patron-barcode-prefix", PolarisConfig.DEFAULT_PATRON_BARCODE_PREFIX),
				Map.entry("services.product-id", polaris.getServicesProductId()),
				Map.entry("services.services-version", polaris.getServicesVersion()),
				Map.entry("services.site-domain", polaris.getServicesSiteDomain()),
				Map.entry("services.workstation-id", String.valueOf(polaris.getServicesWorkstationId())))),
			"org.olf.dcb.core.interaction.sierra.SierraLmsClient", new TreeMap<>(Map.of(
				"api-version", HostLmsSierraApiClient.DEFAULT_API_VERSION,
				"get-hold-delay", String.valueOf(SierraLmsClient.DEFAULT_GET_HOLD_DELAY),
				"get-holds-retry-attempts", String.valueOf(SierraLmsClient.DEFAULT_GET_HOLDS_RETRY_ATTEMPTS),
				"page-size", String.valueOf(IngestSource.DEFAULT_PAGE_SIZE),
				"place-hold-delay", String.valueOf(SierraLmsClient.DEFAULT_PLACE_HOLD_DELAY))));

		final Map<String, TreeMap<String, String>> committed = new JsonMapper().readValue(
			Files.readString(Path.of("..", "docs", "host-lms-defaults.json")),
			new TypeReference<>() { });

		assertThat(new TreeMap<>(fromCode), is(new TreeMap<>(committed)));
	}
}
