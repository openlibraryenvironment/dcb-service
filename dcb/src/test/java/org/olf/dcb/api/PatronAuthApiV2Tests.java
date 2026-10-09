package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.security.RoleNames.INTERNAL_API;
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
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
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
public class PatronAuthApiV2Tests {
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

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-auth-tests.com";
		final String KEY = "patron-auth-key";
		final String SECRET = "patron-auth-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		mockSierra = SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 3600);

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);
	}

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldValidateWhenBarcodeBarcodeAndPinMatches() {
		// Arrange
		final var agencyCode = defineAgency("BASIC/BARCODE+PIN");

		final var barcode = generateBarcode();
		final var pin = "76trombones";

		mockSierra.whenRequest(req -> req
      .withMethod("POST")
      .withPath("/iii/sierra-api/v6/patrons/validate")
      .withBody(json(PatronValidation.builder()
      	.barcode(barcode)
				.pin(pin)
				.build())))
      .respond(HttpResponse.response().withStatusCode(200));

		final var id = generateNumericLocalId();
		final var homeLibraryCode = "home-library-code";

		sierraPatronsAPIFixture.patronFoundResponse("b", barcode,
			SierraPatronRecord.builder()
				.id(id)
				.patronType(LOCAL_PATRON_TYPE)
				.names(List.of("Joe Bloggs"))
				.homeLibraryCode(homeLibraryCode)
				.build());

		savePatronTypeMappings();

		// Act
		final var username = agencyCode + "/" + barcode;

		final var response = authenticatePatron(username, pin);

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));

		final var verificationResponse = response.getBody().get();

		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.username, is(username));
		assertThat(verificationResponse.uniqueIds.get(0), is(id.toString()));
		assertThat(verificationResponse.agencyCode, is(agencyCode));
		assertThat(verificationResponse.systemCode, is(HOST_LMS_CODE));
		assertThat(verificationResponse.homeLocationCode, is(homeLibraryCode));
	}

	@Test
	void shouldRefuseAnAnonymousLookup() {
		final var agencyCode = defineAgency("BASIC/BARCODE+PIN");

		final var lookupRequest = HttpRequest.POST("/v2/patron/auth/lookup",
			V2PatronCredentials.builder().principal(agencyCode + "/" + generateBarcode()).build());

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(client.exchange(lookupRequest, Argument.of(VerificationResponse.class))));

		// DcbAuthorizationExceptionHandler answers a request with no Authorization header with 400
		assertThat(exception.getStatus(), is(BAD_REQUEST));
	}

	@Test
	void shouldRefuseALookupWithADiscoveryCredential() {
		final var agencyCode = defineAgency("BASIC/BARCODE+PIN");

		final var accessToken = "patron-auth2-lookup-discovery-token";

		TestStaticTokenValidator.add(accessToken, "patron-auth2-lookup-discovery",
			List.of(RoleNames.DISCOVERY_SERVICE));

		final var lookupRequest = HttpRequest.POST("/v2/patron/auth/lookup",
				V2PatronCredentials.builder().principal(agencyCode + "/" + generateBarcode()).build())
			.bearerAuth(accessToken);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(client.exchange(lookupRequest, Argument.of(VerificationResponse.class))));

		assertThat(exception.getStatus(), is(UNAUTHORIZED));
	}

	@Test
	void shouldLookUpAPatronForAnInternalApiCaller() {
		// Arrange
		final var agencyCode = defineAgency("BASIC/BARCODE+PIN");
		final var barcode = generateBarcode();
		final var id = generateNumericLocalId();

		sierraPatronsAPIFixture.patronFoundResponse("u", barcode,
			SierraPatronRecord.builder()
				.id(id)
				.patronType(LOCAL_PATRON_TYPE)
				.barcodes(List.of(barcode))
				.names(List.of("Joe Bloggs"))
				.homeLibraryCode("home-library-code")
				.build());

		savePatronTypeMappings();

		final var accessToken = "patron-auth2-lookup-internal-token";

		TestStaticTokenValidator.add(accessToken, "patron-auth2-lookup-internal", List.of(INTERNAL_API));

		// Act
		final var response = singleValueFrom(client.exchange(
			HttpRequest.POST("/v2/patron/auth/lookup",
					V2PatronCredentials.builder().principal(agencyCode + "/" + barcode).build())
				.bearerAuth(accessToken),
			Argument.of(VerificationResponse.class)));

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().map(VerificationResponse::getStatus).orElse(null), is("VALID"));
	}

	private io.micronaut.http.@NonNull HttpResponse<VerificationResponse> authenticatePatron(String username, String pin) {
		final var accessToken = "patron-auth2-test-internal-token";

		TestStaticTokenValidator.add(accessToken, "patron-auth2-test-internal", List.of(INTERNAL_API));

		final var patronCredentials = V2PatronCredentials.builder()
			.principal(username)
			.credentials(pin)
			.build();

		final var postPatronAuthRequest = HttpRequest.POST("/v2/patron/auth", patronCredentials)
			.bearerAuth(accessToken);

		return singleValueFrom(client.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class)));
	}

	private String defineAgency(String authProfile) {
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
	public static class V2PatronCredentials {
		String principal;
		String credentials;
	}

	@Data
	@Serdeable
	@Builder
	public static class VerificationResponse {
		String status;
		String username;
		@Nullable List<String> uniqueIds;
		@Nullable String agencyCode;
		@Nullable String systemCode;
		@Nullable String homeLocationCode;
	}
}
