package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.koha.KohaHostLmsClient;
import org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The two verification checks that read only what DCB holds: whether the stored config has
 * every value the client requires, and whether each agency's auth profile is one the Host LMS
 * can authenticate with.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class HostLmsVerificationProbeTests {
	private static final String POLARIS_BASE_URL = "https://verification-probe-tests.com";

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private HostLmsVerificationService verificationService;

	@Inject
	private HostLmsService hostLmsService;

	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldNameEveryValueAPolarisConfigLacksWithoutCallingPolaris() {
		final var polaris = hostLmsFixture.createPolarisHostLms("probe-polaris", "staff",
			"password", POLARIS_BASE_URL, "DOMAIN", "access-id", "access-key", "agency", 73,
			Map.of("item", Map.of()));

		assertThat(configurationStatus(polaris), is("Missing: item.ill-location-id, "
			+ "item.fine-code-id, item.history-action-id, item.shelving-scheme-id, "
			+ "item.loan-period-code-id, item.av-loan-period-code-id, item.renewal-limit"));
		assertThat(mockServerClient.retrieveRecordedRequests(request()), arrayWithSize(0));
	}

	@Test
	void shouldNameTheMandatorySettingsAKohaConfigLacks() {
		final var koha = hostLmsFixture.createHostLms(randomUUID(), "probe-koha",
			KohaHostLmsClient.class, Optional.empty(), Map.of("api-url",
				"https://koha.example.org/api/v1", "client_id", "client", "client_secret", "secret"));

		assertThat(configurationStatus(koha),
			is("Missing: sharing-library-code, virtual-item-library-code"));
	}

	@Test
	void shouldNotCallAnOptionalSettingMissing() {
		final var sierra = hostLmsFixture.createSierraHostLms("probe-sierra");

		assertThat(configurationStatus(sierra), is("OK"));
	}

	@Test
	void shouldFindTheNcipSystemIdUnderTheSpellingRegistrationWrites() {
		final var appliance = hostLmsFixture.createHostLms(randomUUID(), "probe-appliance",
			ORSApplianceHostLMS.class, Optional.empty(), Map.of(
				ORSApplianceHostLMS.NCIP_ENDPOINT_URL_KEY, "https://appliance.example.org/ncip",
				"ncip-system-id", "probe-system"));

		assertThat(configurationStatus(appliance), is("OK"));
	}

	@Test
	void shouldStillNameAnNcipSystemIdThatIsAbsent() {
		final var appliance = hostLmsFixture.createHostLms(randomUUID(), "probe-appliance",
			ORSApplianceHostLMS.class, Optional.empty(), Map.of(
				ORSApplianceHostLMS.NCIP_ENDPOINT_URL_KEY, "https://appliance.example.org/ncip"));

		assertThat(configurationStatus(appliance), is("Missing: ncipSystemId"));
	}

	// Polaris reads the organisation when the client is built, so no client exists to ask
	@Test
	void shouldSayWhenTheConfigCannotEvenBuildAClient() {
		final var polaris = hostLmsFixture.createPolarisHostLms("probe-polaris", "staff",
			"password", POLARIS_BASE_URL, "DOMAIN", "access-id", "access-key", "agency", 73,
			Map.of("services", Map.of()));

		assertThat(configurationStatus(polaris), is("Failed: client could not be built: "
			+ "Missing required config value: Services Organisation Id"));
	}

	private String configurationStatus(DataHostLms hostLms) {
		return singleValueFrom(verificationService.configurationStatus(
			hostLmsService.getClientFor(hostLms)));
	}

	@Test
	void shouldReportAgenciesWhoseAuthProfileTheHostLmsCannotUse() {
		final var sierra = hostLmsFixture.createSierraHostLms("probe-sierra");
		defineAgency("good-agency", sierra, "BASIC/BARCODE+PIN");
		defineAgency("bad-agency", sierra, "BASIC/BARCODE+PASSWORD");

		assertThat(authProfileStatus(sierra), is("Unsupported: bad-agency=BASIC/BARCODE+PASSWORD. "
			+ "Supported: BASIC/BARCODE+PIN, BASIC/BARCODE+NAME, BASIC/UNIQUE-ID+PIN, BASIC/UNIQUE-ID+NAME"));
	}

	@Test
	void shouldReportAnAgencyWithNoAuthProfileAsUnsupported() {
		final var sierra = hostLmsFixture.createSierraHostLms("probe-sierra");
		defineAgency("good-agency", sierra, "BASIC/BARCODE+PIN");
		defineAgency("no-profile-agency", sierra, null);

		assertThat(authProfileStatus(sierra), is("Unsupported: no-profile-agency=(none). "
			+ "Supported: BASIC/BARCODE+PIN, BASIC/BARCODE+NAME, BASIC/UNIQUE-ID+PIN, BASIC/UNIQUE-ID+NAME"));
	}

	@Test
	void shouldReportOkWhenEveryAgencyUsesASupportedProfile() {
		final var sierra = hostLmsFixture.createSierraHostLms("probe-sierra");
		defineAgency("good-agency", sierra, "BASIC/BARCODE+NAME");

		assertThat(authProfileStatus(sierra), is("OK"));
	}

	@Test
	void shouldSaySoWhenNoAgencyUsesTheHostLmsYet() {
		final var sierra = hostLmsFixture.createSierraHostLms("probe-sierra");

		assertThat(authProfileStatus(sierra), is("Not checked: no agency uses this Host LMS yet"));
	}

	private String authProfileStatus(DataHostLms hostLms) {
		return singleValueFrom(verificationService.authProfileStatus(
			hostLmsFixture.createClient(hostLms.getCode()), hostLms));
	}

	private void defineAgency(String code, DataHostLms hostLms, String authProfile) {
		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.hostLms(hostLms)
			.authProfile(authProfile)
			.build());
	}
}
