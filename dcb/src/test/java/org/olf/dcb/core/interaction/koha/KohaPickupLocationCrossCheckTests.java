package org.olf.dcb.core.interaction.koha;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.interops.InteropTestService;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Every pickup location DCB holds for a Koha is judged against the branches that Koha
 * allows collection from. Until this, a mistyped branch code was found by the first hold
 * Koha refused.
 */
@MockServerMicronautTest
class KohaPickupLocationCrossCheckTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-cross-check";
	private static final String KOHA_HOST = "fake-koha-cross-check";

	@Inject
	private InteropTestService interopTestService;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private LocationFixture locationFixture;

	private DataAgency agency;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var hostLms = hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		agency = agencyFixture.defineAgency("koha-cross-check-agency", "Koha North", hostLms);

		new MockKohaFixture(mockServerClient, KOHA_HOST).mockConfigurationLists(
			KohaLibrary.builder().libraryId("BRANCH-N").name("North Branch").pickupLocation(true).build(),
			KohaLibrary.builder().libraryId("BRANCH-S").name("South Branch").pickupLocation(false).build(),
			KohaLibrary.builder().libraryId("DCB-SHARING").name("Consortium").pickupLocation(true).build());
	}

	@Test
	void shouldJudgeEachPickupLocationAgainstTheBranchesKohaAllowsCollectionFrom() {
		locationFixture.createPickupLocation(randomUUID(), "North Branch", "PICKUP-NORTH", agency, "BRANCH-N");
		locationFixture.createPickupLocation(randomUUID(), "South Branch", "PICKUP-SOUTH", agency, "BRANCH-S");
		locationFixture.createPickupLocation(randomUUID(), "Typo", "PICKUP-TYPO", agency, "BRANCH-NORTH");

		final var report = interopTestService.checkConfiguration(KOHA_HOST_LMS_CODE).block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(check(report, "pickup location PICKUP-NORTH").result(), is(ConfigurationReport.CheckResult.PRESENT));
		assertThat("A branch Koha does not allow collection from",
			check(report, "pickup location PICKUP-SOUTH").result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat("A branch code Koha has never heard of",
			check(report, "pickup location PICKUP-TYPO").result(), is(ConfigurationReport.CheckResult.MISSING));
	}

	@Test
	void shouldSayWhenAPickupLocationNamesNoBranch() {
		locationFixture.createPickupLocation(randomUUID(), "Nameless", "PICKUP-NONE", agency, null);

		final var report = interopTestService.checkConfiguration(KOHA_HOST_LMS_CODE).block();

		assertThat(check(report, "pickup location PICKUP-NONE").result(),
			is(ConfigurationReport.CheckResult.NOT_CONFIGURED));
	}

	private static ConfigurationReport.Check check(ConfigurationReport report, String setting) {
		return report.checks().stream()
			.filter(check -> check.setting().equals(setting))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No check named " + setting + " in " + report.checks()));
	}
}
