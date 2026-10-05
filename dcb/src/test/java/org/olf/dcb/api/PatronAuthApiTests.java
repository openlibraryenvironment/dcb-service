package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalId;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.PatronValidation;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class PatronAuthApiTests {
	private static final String HOST_LMS_CODE = "patron-auth-api-tests";
	private static final Integer LOCAL_PATRON_TYPE = 22;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraTestUtils.MockSierraV6Host mockSierra;

	private String accessToken;


	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-auth-tests.com";
		final String KEY = "patron-auth-key";
		final String SECRET = "patron-auth-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		mockSierra = SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 3600);

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);

		this.accessToken = "patron-auth-tests-token";

		TestStaticTokenValidator.add(accessToken, "test-admin", List.of(ADMINISTRATOR));
	}

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldAuthenticateWhenBarcodeAndPinMatches() {
		// Arrange
		final var agencyCode = defineAgency("BASIC/BARCODE+PIN");

		final var barcode = generateBarcode();
		final var pin = "76trombones";

		final var patronId = generateNumericLocalId();
		final var homeLibraryCode = "home-location-code";

		sierraPatronsAPIFixture.patronFoundResponse("b", barcode,
			SierraPatronRecord.builder()
				.id(patronId)
				.patronType(LOCAL_PATRON_TYPE)
				.homeLibraryCode(homeLibraryCode)
				.build());

		mockSierra.whenRequest(req -> req
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/patrons/validate")
				.withBody(json(PatronValidation.builder()
					.barcode(barcode)
					.pin(pin)
					.build())))
			.respond(HttpResponse.response().withStatusCode(200));

		savePatronTypeMappings();

		// Act
		final var response = authenticatePatron(agencyCode, barcode, pin);

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));

		final var verificationResponse = response.getBody().get();

		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.localPatronId.get(0), is(patronId.toString()));
		assertThat(verificationResponse.agencyCode, is(agencyCode));
		assertThat(verificationResponse.systemCode, is(HOST_LMS_CODE));
		assertThat(verificationResponse.homeLocationCode, is(homeLibraryCode));
	}

	@Test
	void shouldAuthenticateWhenBarcodeAndNameMatches() {
		// Arrange
		final var agencyCode = defineAgency("BASIC/BARCODE+NAME");

		savePatronTypeMappings();

		final var id = generateNumericLocalId();
		final var barcode = generateBarcode();
		final var name = "Joe Bloggs";
		final var homeLocationCode = "home-location-code";

		sierraPatronsAPIFixture.patronFoundResponse("b", barcode,
			SierraPatronRecord.builder()
				.id(id)
				.patronType(LOCAL_PATRON_TYPE)
				.names(List.of(name))
				.homeLibraryCode(homeLocationCode)
				.build());

		// Act
		final var response = authenticatePatron(agencyCode, barcode, name);

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));

		final var verificationResponse = response.getBody().get();

		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.localPatronId.get(0), is(id.toString()));
		assertThat(verificationResponse.agencyCode, is(agencyCode));
		assertThat(verificationResponse.systemCode, is(HOST_LMS_CODE));
		assertThat(verificationResponse.homeLocationCode, is(homeLocationCode));
	}

	@Test
	void shouldFailWhenAuthMethodIsUnknown() {
		// Arrange
		final var agencyCode = defineAgency("UNKNOWN");

		savePatronTypeMappings();

		// Act
		final var response = authenticatePatron(agencyCode, generateBarcode(), "382655gskg");

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));

		final var verificationResponse = response.getBody().get();

		assertThat(verificationResponse.status, is("INVALID"));
		assertThat(verificationResponse.localPatronId, is(nullValue()));
		assertThat(verificationResponse.agencyCode, is(nullValue()));
		assertThat(verificationResponse.systemCode, is(nullValue()));
		assertThat(verificationResponse.homeLocationCode, is(nullValue()));
	}

	private io.micronaut.http.HttpResponse<VerificationResponse> authenticatePatron(String agencyCode,
		String principle, String secret) {

		final var patronCredentials = PatronCredentials.builder()
			.agencyCode(agencyCode)
			.patronPrinciple(principle)
			.secret(secret)
			.build();

		final var postPatronAuthRequest = HttpRequest.POST("/patron/auth", patronCredentials).bearerAuth(accessToken);

		return singleValueFrom(client.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class)));
	}

	private @NonNull String defineAgency(String authProfile) {
		final var agencyCode = "agency-code";

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(agencyCode)
			.name("Agency")
			.authProfile(authProfile)
			.hostLms(hostLmsFixture.findByCode(HOST_LMS_CODE))
			.build());

		return agencyCode;
	}

	private void savePatronTypeMappings() {
		// Without mappings finding the patron fails
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping("patron-auth-api-tests",
			LOCAL_PATRON_TYPE, LOCAL_PATRON_TYPE, "DCB", "dcb-type");
	}

	@Builder
	@Data
	@Serdeable
	public static class PatronCredentials {
		String agencyCode;
		String patronPrinciple;
		String secret;
	}

	@Data
	@Serdeable
	@Builder
	public static class VerificationResponse {
		String status;
		@Nullable List<String> localPatronId;
		@Nullable String agencyCode;
		@Nullable String systemCode;
		@Nullable String homeLocationCode;
	}
}
