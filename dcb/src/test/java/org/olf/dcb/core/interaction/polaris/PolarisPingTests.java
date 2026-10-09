package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/** Both credentials DCB reaches Polaris with are checked, and PAPI's release is reported. */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisPingTests {
	private static final String HOST = "polaris-ping-tests.com";
	private static final String HOST_LMS_CODE = "polaris-ping";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockPolarisFixture mockPolarisFixture;
	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		mockPolarisFixture = new MockPolarisFixture(HOST, mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, "staff-user", "staff-password",
			"https://" + HOST, "TEST", "access-id", "access-key", null, 73,
			Map.of("token-cache-ttl-seconds", "0"));

		mockPolarisFixture.mockAppServicesStaffAuthentication();
		mockPolarisFixture.mockGetHoldRequestDefaults(5);
		mockPolarisFixture.mockPapiApiKeyAccepted();
		mockPolarisFixture.mockPapiApiVersion("7.6.1234");
	}

	@Test
	void shouldReportThePapiReleaseWhenBothCredentialsAreAccepted() {
		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("POLARIS PAPI 7.6.1234"));
	}

	@Test
	void shouldFailWhenPolarisRefusesThePapiAccessKey() {
		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthentication();
		mockPolarisFixture.mockGetHoldRequestDefaults(5);
		mockPolarisFixture.mockPapiApiKeyRefused();

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), containsString("the PAPI access key"));
	}

	@Test
	void shouldFailWhenPolarisRefusesTheStaffLogin() {
		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthenticationAlwaysUnauthorised();
		mockPolarisFixture.mockPapiApiKeyAccepted();

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), containsString("the Application Services staff login"));
		assertThat(response.getAdditional(), containsString("HTTP 401"));
	}

	@Test
	void shouldReportAFailingStaffLoginByItsStatusAndNeverItsBody() {
		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthenticationFailsEchoingHeaders(
			"System.NullReferenceException\r\nHEADERS\r\nAuthorization: Basic c3RhZmYtdXNlcjpzdGFmZi1wYXNzd29yZA==");
		mockPolarisFixture.mockPapiApiKeyAccepted();

		final var response = ping();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), containsString("HTTP 500"));
		assertThat("a Polaris failure is not reported as a refused credential",
			response.getAdditional(), not(containsString("refused")));
		assertThat(response.getAdditional(), not(containsString("Basic")));
	}

	@Test
	void shouldAcceptHoldDefaultsWithNoExpiryPeriod() {
		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthentication();
		mockPolarisFixture.mockGetHoldRequestDefaults(null);
		mockPolarisFixture.mockPapiApiKeyAccepted();
		mockPolarisFixture.mockPapiApiVersion("7.6.1234");

		assertThat(ping().getStatus(), is(PingResponse.OK));
	}

	private PingResponse ping() {
		return singleValueFrom(hostLmsFixture.createClient(HOST_LMS_CODE).ping());
	}

	@Test
	void shouldLogInAfreshEveryPingRatherThanTrustACachedToken() {
		// Caching on: the fixture disables it, which would make this pass for the wrong reason
		hostLmsFixture.createPolarisHostLms("polaris-ping-cached", "staff-user", "staff-password",
			"https://" + HOST, "TEST", "access-id", "access-key", null, 73,
			Map.of("token-cache-ttl-seconds", "900"));

		singleValueFrom(hostLmsFixture.createClient("polaris-ping-cached").ping());
		singleValueFrom(hostLmsFixture.createClient("polaris-ping-cached").ping());

		mockPolarisFixture.verifyAppServicesStaffAuthentication(VerificationTimes.exactly(2));
	}

	@Test
	void shouldReportHowFarPolarisClockIsFromOurs() {
		mockServerClient.reset();
		mockPolarisFixture.mockAppServicesStaffAuthentication();
		mockPolarisFixture.mockGetHoldRequestDefaultsDated(
			ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(120).format(RFC_1123_DATE_TIME));
		mockPolarisFixture.mockPapiApiKeyAccepted();
		mockPolarisFixture.mockPapiApiVersion("7.6.1234");

		final var facts = ping().getFacts();

		assertThat(facts, hasKey("clockSkewSeconds"));
		assertThat("about two minutes behind", ((Long) facts.get("clockSkewSeconds")) <= -115L, is(true));
	}
}
