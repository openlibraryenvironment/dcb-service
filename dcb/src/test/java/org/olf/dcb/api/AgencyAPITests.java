package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasAuthProfile;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasCode;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasId;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasIdpUrl;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.hasName;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.isNotBorrowingAgency;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.isNotSupplyingAgency;
import static org.olf.dcb.test.matchers.AgencyDtoMatchers.isSupplyingAgency;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@DcbTest
@TestInstance(PER_CLASS)
class AgencyAPITests {
	private static final String CIRCULATING_HOST_LMS_CODE = "agency-api-host-lms";
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

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(CIRCULATING_HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToCreateAnAgency() {
		// Act
		final var agency = AgencyDTO.builder()
			.id(randomUUID())
			.code("ab6")
			.name("agencyName")
			.authProfile("authProfile")
			.idpUrl("idpUrl")
			.hostLMSCode(CIRCULATING_HOST_LMS_CODE)
			.isSupplyingAgency(true)
			.isBorrowingAgency(false)
			.build();

		saveAgency(agency);

		// Assert
		final var onlySavedAgency = getOnlyAgency();

		assertThat(onlySavedAgency, allOf(
			notNullValue(),
			hasId(agency.getId()),
			hasCode("ab6"),
			hasName("agencyName"),
			hasAuthProfile("authProfile"),
			hasIdpUrl("idpUrl"),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			isSupplyingAgency(),
			isNotBorrowingAgency()
		));
	}

	@Test
	void shouldBeAbleToUpdateAnAgency() {
		// Arrange
		final var agencyId = randomUUID();

		agencyFixture.defineAgency(DataAgency.builder()
			.id(agencyId)
			.code("agency-code")
			.name("Agency Name")
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLmsFixture.findByCode(CIRCULATING_HOST_LMS_CODE))
			.build());

		// Act
		final var updatedAgency = AgencyDTO.builder()
			.id(agencyId)
			.code("updated-code")
			.name("Updated Name")
			.authProfile("updated-profile")
			.idpUrl("updated-url")
			.hostLMSCode(CIRCULATING_HOST_LMS_CODE)
			.isSupplyingAgency(false)
			.isBorrowingAgency(false)
			.build();

		saveAgency(updatedAgency);

		// Assert
		final var onlySavedAgency = getOnlyAgency();

		assertThat(onlySavedAgency, allOf(
			notNullValue(),
			hasId(agencyId),
			hasCode("updated-code"),
			hasName("Updated Name"),
			hasAuthProfile("updated-profile"),
			hasIdpUrl("updated-url"),
			hasHostLmsCode(CIRCULATING_HOST_LMS_CODE),
			isNotSupplyingAgency(),
			isNotBorrowingAgency()
		));
	}

	@Test
	void shouldFailWhenAgencyAssociatedWithUnknownHostLms() {
		// Act
		final var agency = AgencyDTO.builder()
			.id(randomUUID())
			.code("example-agency")
			.name("Example Agency")
			.authProfile("authProfile")
			.idpUrl("idpUrl")
			.hostLMSCode("unknown-host-lms")
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.build();

		final var blockingClient = client.toBlocking();

		final var postRequest = HttpRequest.POST("/agencies", agency)
			.bearerAuth(ACCESS_TOKEN);

		final var exception = assertThrows(HttpClientResponseException.class, () ->
			blockingClient.exchange(postRequest, AgencyDTO.class));

		// Assert
		final var response = exception.getResponse();

		assertThat("Should return a not found status", response.getStatus(), is(NOT_FOUND));
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
