package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.clients.LoginClient;

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
import services.k_int.interaction.sierra.patrons.InternalPatronValidation;
import services.k_int.interaction.sierra.patrons.PatronValidation;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class PatronAuthApiV2Tests {
	private static final String HOST_LMS_CODE = "patron-auth-api-tests";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private PostgresAgencyRepository agencyRepository;

	@Inject
	private HostLmsFixture hostLmsFixture;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private SierraTestUtils.MockSierraV6Host mockSierra;

	@Inject
	private LoginClient loginClient;

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-auth-tests.com";
		final String KEY = "patron-auth-key";
		final String SECRET = "patron-auth-secret";

		hostLmsFixture.deleteAll();
		agencyRepository.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		mockSierra = SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@Test
	@DisplayName("basic barcode and pin patron auth test")
	void shouldReturnValidStatusWhenUsingBasicBarcodeAndPinValidation() {
		// Arrange
		final var blockingClient = client.toBlocking();
		final var accessToken = loginClient.getAccessToken();
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab7").name("agencyName")
			.authProfile("BASIC/BARCODE+PIN").idpUrl("idpUrl").hostLMSCode(HOST_LMS_CODE).build();
		final var agencyRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);
		blockingClient.exchange(agencyRequest, AgencyDTO.class);
		final var patronCredentials = V2PatronCredentials.builder().principal("ab7/3100222227777").credentials("76trombones").build();
		final var postPatronAuthRequest = HttpRequest.POST("/v2/patron/auth", patronCredentials).bearerAuth(accessToken);

    mockSierra.whenRequest(req -> req
      .withMethod("POST")
      .withPath("/iii/sierra-api/v6/patrons/validate")
      .withBody(json(PatronValidation.builder()
      .barcode("3100222227777").pin("76trombones").build())))
      .respond(HttpResponse.response().withStatusCode(200));

    sierraPatronsAPIFixture.patronResponseForUniqueId("b", "3100222227777");
    savePatronTypeMappings();

		// Act
		final var response = blockingClient.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class));

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));

		VerificationResponse verificationResponse = response.getBody().get();
		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.username, is("ab7/3100222227777"));
		assertThat(verificationResponse.uniqueIds.get(0), is("1000002"));
		assertThat(verificationResponse.agencyCode, is("ab7"));
		assertThat(verificationResponse.systemCode, is("patron-auth-api-tests"));
		// assertThat(verificationResponse.homeLocationCode, is("testccc"));
		assertThat(verificationResponse.homeLocationCode, is("testbbb"));
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

	private void savePatronTypeMappings() {
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping("patron-auth-api-tests", 10, 25, "DCB", "15");
		referenceValueMappingFixture.definePatronTypeMapping("DCB", "15", "patron-auth-api-tests", "15");
	}
}
