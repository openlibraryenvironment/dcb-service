package org.olf.dcb.api;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.test.HostLmsFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false, rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgencyAPITests {
	private final Logger log = LoggerFactory.getLogger(PatronRequestApiTests.class);

	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private PostgresAgencyRepository agencyRepository;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		agencyRepository.deleteAll();
	}

	@Test
	void shouldReturnAgenciesByUsingAPItoSaveAndList() {
		// Arrange
                log.info("get client");
		final var blockingClient = client.toBlocking();
                log.info("create hostLmsCode");
		final var hostLms = hostLmsFixture.createHostLms(randomUUID(), "hostLmsCode");
		final var accessToken = getAccessToken(blockingClient);
                log.info("create agency");
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab6").name("agencyName")
			.authProfile("authProfile").idpUrl("idpUrl").hostLMSCode("hostLmsCode").build();
                log.info("get agencies");
		final var postRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);
		final var postResponse = blockingClient.exchange(postRequest, AgencyDTO.class);
                log.info("get agencies page");
		final var listRequest = HttpRequest.GET("/agencies?page=0&size=10").bearerAuth(accessToken);
		// Act
		final var listResponse = blockingClient.exchange(listRequest, Argument.of(Page.class, AgencyDTO.class));
		// Assert
		assertThat(listResponse.getStatus(), is(OK));
		assertThat(listResponse.getBody().isPresent(), is(true));
		
		Page<AgencyDTO> page = listResponse.getBody().get();
		final var onlySavedAgency = page.getContent().get(0);
		assertThat(page.getContent().size(), is(1));
		assertThat(onlySavedAgency.getId(), is(agencyDTO.getId()));
		assertThat(onlySavedAgency.getCode(), is("ab6"));
		assertThat(onlySavedAgency.getName(), is("agencyName"));
		assertThat(onlySavedAgency.getAuthProfile(), is("authProfile"));
		assertThat(onlySavedAgency.getIdpUrl(), is("idpUrl"));
		assertThat(onlySavedAgency.getHostLMSCode(), is("hostLmsCode")); // showing work around works
	}

	private static String getAccessToken(BlockingHttpClient blockingClient) {
		final var creds = new UsernamePasswordCredentials("admin", "password");
		final var loginRequest = HttpRequest.POST("/login", creds);
		final var loginResponse = blockingClient.exchange(loginRequest, BearerAccessRefreshToken.class);
		final var bearerAccessRefreshToken = loginResponse.body();
		final var accessToken = bearerAccessRefreshToken.getAccessToken();
		return accessToken;
	}
}
