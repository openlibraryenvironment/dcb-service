package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

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
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToCreateAgency() {
		// Arrange
		log.info("get client");
		log.info("create hostLmsCode");

		hostLmsFixture.createSierraHostLms("hostLmsCode");

		final var accessToken = "agency-test-admin-token";
		TestStaticTokenValidator.add(accessToken, "test-admin", List.of(ADMINISTRATOR));

		log.info("create agency");
		final var agencyDTO = AgencyDTO.builder()
			.id(randomUUID())
			.code("ab6")
			.name("agencyName")
			.authProfile("authProfile")
			.idpUrl("idpUrl")
			.hostLMSCode("hostLmsCode")
			.isSupplyingAgency(true)
			.isBorrowingAgency(false)
			.build();

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
		
		final var page = listResponse.getBody().get();

		final var pageContent = page.getContent();

		assertThat("Should only find one agency", pageContent.size(), is(1));

		final var onlySavedAgency = pageContent.get(0);

		assertThat(onlySavedAgency, allOf(
			notNullValue(),
			hasProperty("id", is(agencyDTO.getId())),
			hasProperty("code", is("ab6")),
			hasProperty("name", is("agencyName")),
			hasProperty("authProfile", is("authProfile")),
			hasProperty("idpUrl", is("idpUrl")),
			hasProperty("hostLMSCode", is("hostLmsCode")),
			hasProperty("isSupplyingAgency", is(true)),
			hasProperty("isBorrowingAgency", is(false))
		));
	}
}
