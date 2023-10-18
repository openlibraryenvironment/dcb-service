package org.olf.dcb.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.*;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.InternalPatronValidation;
import services.k_int.test.mockserver.MockServerMicronautTest;
import java.io.IOException;
import java.util.List;
import io.micronaut.core.annotation.Nullable;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.JsonBody.json;

@MockServerMicronautTest
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PatronAuthApiTests {
	private final Logger log = LoggerFactory.getLogger(PatronAuthApiTests.class);
	private static final String HOST_LMS_CODE = "patron-auth-api-tests";
	@Inject
	private ResourceLoader loader;
	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private PostgresAgencyRepository agencyRepository;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	SierraTestUtils.MockSierraV6Host mockSierra;

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mock) throws IOException {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-auth-tests.com";
		final String KEY = "patron-auth-key";
		final String SECRET = "patron-auth-secret";

		hostLmsFixture.deleteAll();
		agencyRepository.deleteAll();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);

		mockSierra = SierraTestUtils.mockFor(mock, BASE_URL).setValidCredentials(KEY, SECRET, TOKEN, 60);
		this.sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
	}

	@Test
	@DisplayName("basic barcode and pin patron auth test")
	void shouldReturnValidStatusWhenUsingBasicBarcodeAndPinValidation() {
		// Arrange
		final var blockingClient = client.toBlocking();
		final var accessToken = getAccessToken(blockingClient);
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab7").name("agencyName")
			.authProfile("BASIC/BARCODE+PIN").idpUrl("idpUrl").hostLMSCode(HOST_LMS_CODE).build();
		final var agencyRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);
		final var agency = blockingClient.exchange(agencyRequest, AgencyDTO.class);
		final var patronCredentials = PatronCredentials.builder().agencyCode("ab7")
			.patronPrinciple("3100222227777").secret("76trombones").build();
		final var postPatronAuthRequest = HttpRequest.POST("/patron/auth", patronCredentials).bearerAuth(accessToken);
		mockSierra.whenRequest(req -> req
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/patrons/auth")
				.withBody(json(InternalPatronValidation.builder().authMethod("native")
					.patronId("3100222227777").patronSecret("76trombones").build())))
			.respond(HttpResponse.response("23945734234"));
		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse("23945734234");
		// Act
		final var response = blockingClient.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class));
		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));
		VerificationResponse verificationResponse = response.getBody().get();
		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.localPatronId.get(0), is("1000002"));
		assertThat(verificationResponse.agencyCode, is("ab7"));
		assertThat(verificationResponse.systemCode, is("patron-auth-api-tests"));
		assertThat(verificationResponse.homeLocationCode, is("testccc"));
	}

	@Test
	@DisplayName("basic barcode and name patron auth test")
	void shouldReturnValidStatusWhenUsingBasicBarcodeAndNameValidation() {
		final var blockingClient = client.toBlocking();
		final var accessToken = getAccessToken(blockingClient);
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab6").name("agencyName")
			.authProfile("BASIC/BARCODE+NAME").idpUrl("idpUrl").hostLMSCode(HOST_LMS_CODE).build();
		final var agencyRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);
		final var agency = blockingClient.exchange(agencyRequest, AgencyDTO.class);
		final var patronCredentials = PatronCredentials.builder().agencyCode("ab6")
			.patronPrinciple("3100222227777").secret("Joe Bloggs").build();
		final var postPatronAuthRequest = HttpRequest.POST("/patron/auth", patronCredentials).bearerAuth(accessToken);
		sierraPatronsAPIFixture.patronResponseForUniqueId("b", "3100222227777");
		// Act
		final var response = blockingClient.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class));
		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));
		VerificationResponse verificationResponse = response.getBody().get();
		assertThat(verificationResponse.status, is("VALID"));
		assertThat(verificationResponse.localPatronId.get(0), is("1000002"));
		assertThat(verificationResponse.agencyCode, is("ab6"));
		assertThat(verificationResponse.systemCode, is("patron-auth-api-tests"));
		assertThat(verificationResponse.homeLocationCode, is("testbbb"));
	}

	@Test
	@DisplayName("Unknown auth method test")
	void shouldReturnInvalidStatusWhenUnknownAuthMethod() {
		final var blockingClient = client.toBlocking();
		final var accessToken = getAccessToken(blockingClient);
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab8").name("agencyName")
			.authProfile("UNKNOWN").hostLMSCode(HOST_LMS_CODE).build();
		final var agencyRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);
		final var agency = blockingClient.exchange(agencyRequest, AgencyDTO.class);
		final var patronCredentials = PatronCredentials.builder().agencyCode("ab8")
			.patronPrinciple("4239058490").secret("10398473").build();
		final var postPatronAuthRequest = HttpRequest.POST("/patron/auth", patronCredentials).bearerAuth(accessToken);
		// Act
		final var response = blockingClient.exchange(postPatronAuthRequest, Argument.of(VerificationResponse.class));
		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));
		VerificationResponse verificationResponse = response.getBody().get();
		assertThat(verificationResponse.status, is("INVALID"));
		assertThat(verificationResponse.localPatronId, is(nullValue()));
		assertThat(verificationResponse.agencyCode, is(nullValue()));
		assertThat(verificationResponse.systemCode, is(nullValue()));
		assertThat(verificationResponse.homeLocationCode, is(nullValue()));
	}

	private static String getAccessToken(BlockingHttpClient blockingClient) {
		final var creds = new UsernamePasswordCredentials("admin", "password");
		final var loginRequest = HttpRequest.POST("/login", creds);
		final var loginResponse = blockingClient.exchange(loginRequest, BearerAccessRefreshToken.class);
		final var bearerAccessRefreshToken = loginResponse.body();
		final var accessToken = bearerAccessRefreshToken.getAccessToken();
		return accessToken;
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
