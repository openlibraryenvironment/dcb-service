package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

/** The two limits on patron checks: per member of staff, and per library system. */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Property(name = "dcb.patron-checks.per-caller-per-minute", value = "2")
@Property(name = "dcb.patron-checks.per-host-lms-per-minute", value = "3")
class PatronCheckRateLimitApiTests {
	private static final String HOST_LMS = "patron-check-limit-sierra";
	private static final String BASE_URL = "https://patron-check-limit-tests.com";
	private static final String AGENCY = "check-limit";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private SierraPatronsAPIFixture sierraPatrons;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var key = "patron-check-limit-key";
		final var secret = "patron-check-limit-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(key, secret, "patron-check-limit-token", 3600);

		for (final var staff : List.of("first", "second")) {
			TestStaticTokenValidator.add("patron-check-limit-" + staff, staff,
				List.of(LIBRARY_ADMIN), Map.of("code", AGENCY));
		}

		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(AGENCY)
			.name(AGENCY)
			.isBorrowingAgency(true)
			.hostLms(hostLmsFixture.createSierraHostLms(HOST_LMS, key, secret, BASE_URL, "item"))
			.build());

		sierraPatrons = sierraApiFixtureProvider.patrons(mockServerClient, null);
	}

	@AfterAll
	void afterAll() {
		agencyFixture.deleteAll();
	}

	@Test
	void aMemberOfStaffIsLimitedThenTheLibrarySystemIs() {
		assertThat(checkAs("first"), is(HttpStatus.OK));
		assertThat(checkAs("first"), is(HttpStatus.OK));

		final var callerLimited = refusalAs("first");

		assertThat(callerLimited.getStatus(), is(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(callerLimited.getResponse().header("Retry-After"), is("60"));

		// The system has answered two; a second member of staff gets the third and no more
		assertThat(checkAs("second"), is(HttpStatus.OK));

		final var systemLimited = refusalAs("second");

		assertThat(systemLimited.getStatus(), is(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(systemLimited.getResponse().header("Retry-After"), is("60"));
	}

	private HttpStatus checkAs(String staff) {
		final var barcode = generateBarcode();

		sierraPatrons.patronNotFoundResponse("u", barcode);
		sierraPatrons.patronNotFoundResponse("b", barcode);

		return client.toBlocking().exchange(request(staff, barcode)).getStatus();
	}

	private HttpClientResponseException refusalAs(String staff) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request(staff, generateBarcode())));
	}

	private static HttpRequest<?> request(String staff, String barcode) {
		return HttpRequest.POST("/patrons/eligibility-checks", Map.of("identifier", barcode))
			.bearerAuth("patron-check-limit-" + staff);
	}
}
