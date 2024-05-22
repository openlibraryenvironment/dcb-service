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

import org.junit.jupiter.api.BeforeAll;
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
	private static final String ACCESS_TOKEN = "agency-test-admin-token";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "test-admin", List.of(ADMINISTRATOR));
	}

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToCreateAgency() {
		// Arrange
		hostLmsFixture.createSierraHostLms("hostLmsCode");

		// Act
		final var agency = AgencyDTO.builder()
			.id(randomUUID())
			.code("ab6")
			.name("agencyName")
			.authProfile("authProfile")
			.idpUrl("idpUrl")
			.hostLMSCode("hostLmsCode")
			.isSupplyingAgency(true)
			.isBorrowingAgency(false)
			.build();


		saveAgency(agency);

		// Assert
		final var onlySavedAgency = getOnlyAgency();

		assertThat(onlySavedAgency, allOf(
			notNullValue(),
			hasProperty("id", is(agency.getId())),
			hasProperty("code", is("ab6")),
			hasProperty("name", is("agencyName")),
			hasProperty("authProfile", is("authProfile")),
			hasProperty("idpUrl", is("idpUrl")),
			hasProperty("hostLMSCode", is("hostLmsCode")),
			hasProperty("isSupplyingAgency", is(true)),
			hasProperty("isBorrowingAgency", is(false))
		));
	}

	private void saveAgency(AgencyDTO agencyToSave) {
		final var blockingClient = client.toBlocking();

		final var postRequest = HttpRequest.POST("/agencies", agencyToSave)
			.bearerAuth(ACCESS_TOKEN);

		blockingClient.exchange(postRequest, AgencyDTO.class);
	}

	private AgencyDTO getOnlyAgency() {
		final var agencyListRequest = HttpRequest.GET("/agencies?page=0&size=1")
			.bearerAuth(ACCESS_TOKEN);

		final var agencyListResponse = client.toBlocking()
			.exchange(agencyListRequest, Argument.of(Page.class, AgencyDTO.class));

		assertThat(agencyListResponse.getStatus(), is(OK));
		assertThat(agencyListResponse.getBody().isPresent(), is(true));

		final var page = agencyListResponse.getBody().get();

		final var pageContent = page.getContent();

		assertThat("Should only find one agency", pageContent.size(), is(1));

		return (AgencyDTO)pageContent.get(0);
	}
}
