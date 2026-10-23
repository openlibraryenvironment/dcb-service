package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.api.PatronAuthApiTests.PatronCredentials;
import org.olf.dcb.api.PatronAuthApiTests.VerificationResponse;
import org.olf.dcb.core.interaction.koha.MockKohaFixture;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronAuthResponse;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A Koha patron signing in through DCB's patron authentication API. The request and the
 * answer are the contract a discovery layer depends on; what happens between them is the
 * adapter's business.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class KohaPatronAuthApiTests {
	private static final String HOST_LMS_CODE = "koha-patron-auth-api";
	private static final String KOHA_HOST = "fake-koha-patron-auth-api";
	private static final String AGENCY_CODE = "koha-patron-auth-agency";
	private static final String PATRON_ID = "12";
	private static final String CARDNUMBER = "CARD-0001";
	private static final String PASSWORD = "correct-horse-battery";
	private static final String HOME_BRANCH = "BRANCH-N";

	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockKohaFixture koha;
	private String accessToken;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(HOST_LMS_CODE, "https://" + KOHA_HOST);

		accessToken = "koha-patron-auth-api-token";
		TestStaticTokenValidator.add(accessToken, "test-admin", List.of(ADMINISTRATOR));
	}

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(AGENCY_CODE)
			.name("Koha North")
			.authProfile("BASIC/BARCODE+PASSWORD")
			.hostLms(hostLmsFixture.findByCode(HOST_LMS_CODE))
			.build());
		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE, "AD", "DCB", "15");

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
	}

	@Test
	void shouldAuthenticateWhenKohaAcceptsThePassword() {
		koha.mockPasswordAccepted(CARDNUMBER, PASSWORD, KohaPatronAuthResponse.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber(CARDNUMBER)
			.userid("grace")
			.build());
		koha.mockPatron(KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber(CARDNUMBER)
			.surname("Example")
			.firstname("Grace")
			.libraryId(HOME_BRANCH)
			.categoryId("AD")
			.expiryDate("2099-12-31")
			.build());

		final var response = authenticatePatron(CARDNUMBER, PASSWORD);

		assertThat(response.getStatus(), is(OK));
		final var verification = response.getBody().orElseThrow();
		assertThat(verification.getStatus(), is("VALID"));
		assertThat(verification.getLocalPatronId(), contains(PATRON_ID));
		assertThat(verification.getAgencyCode(), is(AGENCY_CODE));
		assertThat(verification.getSystemCode(), is(HOST_LMS_CODE));
		assertThat("The branch Koha holds for the patron",
			verification.getHomeLocationCode(), is(HOME_BRANCH));
	}

	@Test
	void shouldRefuseWhenKohaRejectsThePassword() {
		koha.mockPasswordRefused(CARDNUMBER, "wrong", 401);

		final var response = authenticatePatron(CARDNUMBER, "wrong");

		assertThat(response.getStatus(), is(OK));
		final var verification = response.getBody().orElseThrow();
		assertThat(verification.getStatus(), is("INVALID"));
		assertThat("Nothing about the patron leaves DCB on a refusal",
			verification.getLocalPatronId(), is(nullValue()));
	}

	private HttpResponse<VerificationResponse> authenticatePatron(String cardnumber, String secret) {
		final var credentials = PatronCredentials.builder()
			.agencyCode(AGENCY_CODE)
			.patronPrinciple(cardnumber)
			.secret(secret)
			.build();

		return singleValueFrom(client.exchange(
			HttpRequest.POST("/patron/auth", credentials).bearerAuth(accessToken),
			Argument.of(VerificationResponse.class)));
	}
}
