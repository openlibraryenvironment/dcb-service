package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.clients.LoginClient;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@DcbTest
@TestInstance(PER_CLASS)
class AgencyAPITests {
	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private PostgresAgencyRepository agencyRepository;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private LoginClient loginClient;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		agencyRepository.deleteAll();
	}

	@Test
	void shouldReturnAgenciesByUsingAPItoSaveAndList() {
		// Arrange
		log.info("get client");
		log.info("create hostLmsCode");

		hostLmsFixture.createSierraHostLms("hostLmsCode");
		final var accessToken = "agency-test-admin-token";
		TestStaticTokenValidator.add(accessToken, "test-admin", List.of(RoleNames.ADMINISTRATOR));
		

		log.info("create agency");
		final var agencyDTO = AgencyDTO.builder().id(randomUUID()).code("ab6").name("agencyName")
			.authProfile("authProfile").idpUrl("idpUrl").hostLMSCode("hostLmsCode").build();

		log.info("get agencies");
		final var blockingClient = client.toBlocking();
		final var postRequest = HttpRequest.POST("/agencies", agencyDTO).bearerAuth(accessToken);

		blockingClient.exchange(postRequest, AgencyDTO.class);

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
}
