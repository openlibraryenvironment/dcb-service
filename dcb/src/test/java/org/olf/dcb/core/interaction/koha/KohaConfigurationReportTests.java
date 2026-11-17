package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaItemType;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronCategory;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What the configuration check tells an implementer about a Koha. A pickup location's
 * localId has to be a branch Koha allows collection from, and the sharing branch is one
 * such branch, so the report says which branches those are and checks the setting
 * against them rather than against every branch.
 */
@MockServerMicronautTest
class KohaConfigurationReportTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-configuration-report";
	private static final String KOHA_HOST = "fake-koha-configuration-report";
	private static final List<String> PREFERENCES = List.of("AllowHoldPolicyOverride", "IndependentBranches",
		"canreservefromotherbranches", "ItemsDeniedRenewal", "OpacSuppression", "ReservesNeedReturns");

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		givenKohaLists();
	}

	@Test
	void shouldListTheBranchesKohaAllowsCollectionFrom() {
		givenPreferences(Map.of());

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(vocabulary(report, "Libraries").entries(), contains(
			entry("BRANCH-N", "North Branch"), entry("BRANCH-S", "South Branch"),
			entry("DCB-SHARING", "Consortium")));
		assertThat(vocabulary(report, "Pickup libraries").entries(), contains(
			entry("BRANCH-N", "North Branch"), entry("DCB-SHARING", "Consortium")));
	}

	@Test
	void shouldAcceptASharingBranchKohaAllowsCollectionFrom() {
		givenPreferences(Map.of());

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(check(report, "sharing-library-code").result(),
			is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldReportASharingBranchKohaDoesNotAllowCollectionFrom() {
		// Every supplier hold names the sharing branch as its pickup branch; Koha refuses
		// a hold at a branch that is not a pickup location, so the setting is wrong
		givenPreferences(Map.of());

		final var report = client("BRANCH-S").checkConfiguration().block();

		assertThat(check(report, "sharing-library-code").result(),
			is(ConfigurationReport.CheckResult.MISSING));
	}

	@Test
	void shouldAcceptThePreferencesWhenTheySayWhatDcbNeeds() {
		givenPreferences(Map.of(
			"AllowHoldPolicyOverride", "1", "IndependentBranches", "0", "canreservefromotherbranches", "0",
			"ItemsDeniedRenewal", "ccode: [DCB_NO_RENEW]", "OpacSuppression", "1", "ReservesNeedReturns", "1"));

		final var report = client("DCB-SHARING").checkConfiguration().block();

		for (String name : PREFERENCES) {
			assertThat(name, check(report, name).result(), is(ConfigurationReport.CheckResult.PRESENT));
		}
	}

	@Test
	void shouldReportIndependentBranchesWithoutHoldsFromOtherBranches() {
		// Together these refuse every supplier hold and every co-tenant local request
		givenPreferences(Map.of("IndependentBranches", "1", "canreservefromotherbranches", "0",
			"AllowHoldPolicyOverride", "0", "ItemsDeniedRenewal", "", "OpacSuppression", "0", "ReservesNeedReturns", "0"));

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(check(report, "IndependentBranches").result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check(report, "canreservefromotherbranches").result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check(report, "AllowHoldPolicyOverride").result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check(report, "ItemsDeniedRenewal").result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check(report, "ReservesNeedReturns").result(), is(ConfigurationReport.CheckResult.MISSING));
	}

	@Test
	void shouldSayWhenAKohaCannotBeAskedForItsPreferences() {
		// Before 26.05 there is no route; on 26.05 the API patron may lack manage_sysprefs
		koha.mockNoSystemPreferencesRoute();

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(check(report, "IndependentBranches").result(), is(ConfigurationReport.CheckResult.UNKNOWN));
		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
	}

	/** A Koha 26.05 answering each preference DCB asks about: its value, or no value. */
	private void givenPreferences(Map<String, String> preferences) {
		for (String name : PREFERENCES) {
			if (preferences.containsKey(name)) {
				koha.mockSystemPreference(name, preferences.get(name));
			} else {
				koha.mockSystemPreferenceUnset(name);
			}
		}
	}

	private void givenKohaLists() {
		koha.mockItemTypes(KohaItemType.builder().itemTypeId("BK").description("Book").build());
		koha.mockPatronCategories(KohaPatronCategory.builder().patronCategoryId("DCB").name("Consortium patron").build());
		koha.mockLibraries(
			KohaLibrary.builder().libraryId("BRANCH-N").name("North Branch").pickupLocation(true).build(),
			KohaLibrary.builder().libraryId("BRANCH-S").name("South Branch").pickupLocation(false).build(),
			KohaLibrary.builder().libraryId("DCB-SHARING").name("Consortium").pickupLocation(true).build());
	}

	private static ConfigurationReport.Vocabulary vocabulary(ConfigurationReport report, String name) {
		return report.vocabularies().stream()
			.filter(vocabulary -> vocabulary.name().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private static ConfigurationReport.Check check(ConfigurationReport report, String setting) {
		return report.checks().stream()
			.filter(check -> check.setting().equals(setting))
			.findFirst()
			.orElseThrow();
	}

	private static ConfigurationReport.Entry entry(String code, String description) {
		return new ConfigurationReport.Entry(code, description);
	}

	private HostLmsClient client(String sharingLibraryCode) {
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, Map.of(
			"sharing-library-code", sharingLibraryCode,
			"virtual-item-library-code", "BRANCH-N"));

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}
}
