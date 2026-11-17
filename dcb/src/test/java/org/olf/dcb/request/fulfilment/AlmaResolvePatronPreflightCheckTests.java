package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.HttpRequest.request;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The preflight check against an Alma borrower: a patron whose user group is mapped as
 * ineligible is refused before any supplier is asked, and one in an eligible group passes.
 */
@MockServerMicronautTest
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "true")
class AlmaResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String ALMA_HOST_LMS_CODE = "alma-borrowing-host-lms";
	private static final String ALMA_URL = "https://fake-alma-preflight";
	private static final String AGENCY_CODE = "alma-north";
	private static final String PATRON_ID = "SIS123";

	@Inject
	private ResolvePatronPreflightCheck check;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PatronFixture patronFixture;

	private MockServerClient mockServerClient;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		mockServerClient.reset();
		patronFixture.deleteAllPatrons();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var hostLms = hostLmsFixture.createAlmaHostLms(ALMA_HOST_LMS_CODE, ALMA_URL,
			Map.of("default-agency-code", AGENCY_CODE));

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(AGENCY_CODE)
			.name("Alma North")
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLms)
			.build());
		referenceValueMappingFixture.definePatronTypeMapping(ALMA_HOST_LMS_CODE, "UNDRGRD", "DCB", "UNDERGRADUATE");
		referenceValueMappingFixture.definePatronTypeMapping(ALMA_HOST_LMS_CODE, "UBPatron", "DCB", "NOT_ELIGIBLE");
	}

	@Test
	void shouldPassAPatronInAnEligibleUserGroup() {
		mockAlmaUser("UNDRGRD");

		assertThat(check(), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldRefuseAPatronWhoseUserGroupIsMappedAsIneligible() {
		mockAlmaUser("UBPatron");

		assertThat(check(), containsInAnyOrder(failedCheck("PATRON_INELIGIBLE")));
	}

	private List<CheckResult> check() {
		return singleValueFrom(check.check(placeRequestCommand("pickup-location",
			ALMA_HOST_LMS_CODE, PATRON_ID, randomUUID())));
	}

	private void mockAlmaUser(String userGroup) {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/" + PATRON_ID))
			.respond(okJson(Map.of(
				"primary_id", PATRON_ID,
				"first_name", "Grace",
				"last_name", "Example",
				"status", Map.of("value", "ACTIVE"),
				"user_group", Map.of("value", userGroup),
				"user_identifier", List.of(Map.of(
					"id_type", Map.of("value", "BARCODE"),
					"value", "CARD-0001")))));
	}
}
