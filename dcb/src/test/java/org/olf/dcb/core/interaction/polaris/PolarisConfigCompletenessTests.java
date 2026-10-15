package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.ConfigCompleteness.acceptedHostLms;
import static org.olf.dcb.test.ConfigCompleteness.unreadable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;

class PolarisConfigCompletenessTests {
	private static final String POLARIS = "org.olf.dcb.core.interaction.polaris.PolarisLmsClient";

	@Test
	void shouldReadEveryValueTheClientNeedsFromAConfigTheValidatorAccepts() {
		final var config = PolarisLmsClient.convertConfig(acceptedHostLms(POLARIS, minimalConfig()));

		final var reads = new LinkedHashMap<String, Callable<?>>();
		reads.put("getBaseUrl", config::getBaseUrl);
		reads.put("getLogonBranchId", config::getLogonBranchId);
		reads.put("getLogonUserId", config::getLogonUserId);
		reads.put("getPapiVersion", config::getPapiVersion);
		reads.put("getPapiLangId", config::getPapiLangId);
		reads.put("getPapiAppId", config::getPapiAppId);
		reads.put("getPapiOrgId", config::getPapiOrgId);
		reads.put("getServicesVersion", config::getServicesVersion);
		reads.put("getServicesLanguage", config::getServicesLanguage);
		reads.put("getServicesProductId", config::getServicesProductId);
		reads.put("getServicesSiteDomain", config::getServicesSiteDomain);
		reads.put("getServicesOrganisationId", config::getServicesOrganisationId);
		reads.put("getServicesWorkstationId", config::getServicesWorkstationId);
		reads.put("getItemRenewalLimit", config::getItemRenewalLimit);
		reads.put("getItemAvRenewalLimit", config::getItemAvRenewalLimit);
		reads.put("getItemFindCodeId", config::getItemFindCodeId);
		reads.put("getItemHistoryActionId", config::getItemHistoryActionId);
		reads.put("getItemLoanPeriodCodeId", config::getItemLoanPeriodCodeId);
		reads.put("getItemAvLoanPeriodCodeId", config::getItemAvLoanPeriodCodeId);
		reads.put("getItemShelvingSchemeId", config::getItemShelvingSchemeId);
		reads.put("getItemBarcodePrefix", config::getItemBarcodePrefix);
		reads.put("getIllLocationId", config::getIllLocationId);
		reads.put("getTokenCacheMaxTtl", config::getTokenCacheMaxTtl);
		reads.put("getHarvestWatermarkOverlap", config::getHarvestWatermarkOverlap);

		final var failures = unreadable(reads);

		assertThat("Unreadable from an accepted config: " + failures, failures, is(Map.of()));
	}

	@Test
	void shouldReadTheAvRenewalLimitWhenOneIsSet() {
		final var config = configWithItem(Map.of("renewal-limit", 2, "av-renewal-limit", 1));

		assertThat(config.getItemRenewalLimit(), is(2));
		assertThat(config.getItemAvRenewalLimit(), is(1));
	}

	@Test
	void shouldUseTheRenewalLimitForAvItemsWhenNoAvLimitIsSet() {
		assertThat(configWithItem(Map.of("renewal-limit", 2)).getItemAvRenewalLimit(), is(2));
	}

	// The validator runs on create and update only: a record saved before these keys were
	// required must still bind, and fail only when a request reads the missing value
	@Test
	void shouldStillBindAStoredConfigThatLacksARequiredValue() {
		final var config = configWithItem(Map.of("renewal-limit", 2));

		assertThat(config.getBaseUrl(), is("https://polaris.example.org"));
	}

	private static PolarisConfig configWithItem(Map<String, Object> item) {
		final var stored = new LinkedHashMap<>(minimalConfig());
		stored.put("item", item);

		return PolarisLmsClient.convertConfig(DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("polaris-config")
			.clientConfig(stored)
			.build());
	}

	private static Map<String, Object> minimalConfig() {
		final var config = new LinkedHashMap<String, Object>();
		config.put("base-url", "https://polaris.example.org");
		config.put("access-id", "access-id");
		config.put("access-key", "access-key");
		config.put("domain-id", "DOMAIN");
		config.put("logon-branch-id", "73");
		config.put("logon-user-id", "1");
		config.put("staff-username", "staff");
		config.put("staff-password", "password");
		config.put("default-agency-code", "agency");
		config.put("papi", Map.of());
		config.put("services", Map.of("organisation-id", "1"));
		config.put("item", Map.of("ill-location-id", 73, "fine-code-id", 1, "history-action-id", 6,
			"shelving-scheme-id", 1, "loan-period-code-id", 7, "av-loan-period-code-id", 8, "renewal-limit", 0));
		return config;
	}
}
