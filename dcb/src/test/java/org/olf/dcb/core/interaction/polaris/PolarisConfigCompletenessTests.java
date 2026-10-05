package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.ConfigCompleteness.acceptedHostLms;
import static org.olf.dcb.test.ConfigCompleteness.unreadable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

class PolarisConfigCompletenessTests {
	private static final String POLARIS = "org.olf.dcb.core.interaction.polaris.PolarisLmsClient";

	// Read at run time and accepted absent by HostLmsConfigValidator. Remove an entry when the
	// validator starts requiring its key; a stale entry fails this test as surely as a new gap
	private static final Set<String> KNOWN_GAPS = Set.of(
		"getServicesOrganisationId",
		"getItemRenewalLimit",
		"getItemAvRenewalLimit",
		"getItemFindCodeId",
		"getItemHistoryActionId",
		"getItemLoanPeriodCodeId",
		"getItemAvLoanPeriodCodeId",
		"getItemShelvingSchemeId",
		"getIllLocationId");

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

		assertThat("Unreadable from an accepted config: " + failures, failures.keySet(), is(KNOWN_GAPS));
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
		config.put("services", Map.of());
		config.put("item", Map.of());
		return config;
	}
}
