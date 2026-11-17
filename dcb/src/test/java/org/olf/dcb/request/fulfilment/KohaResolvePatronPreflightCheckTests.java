package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.koha.MockKohaFixture;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The preflight check against a Koha borrower: a patron Koha has restricted, whose card
 * is lost, or whose account has expired is refused before any supplier is asked, and a
 * patron Koha is content with passes. The patron is read by the id DCB holds.
 */
@MockServerMicronautTest
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "true")
class KohaResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-borrowing-host-lms";
	private static final String KOHA_HOST = "fake-koha-preflight";
	private static final String HOME_BRANCH = "BRANCH-N";
	private static final String AGENCY_CODE = "koha-north";
	private static final String PATRON_ID = "12";

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

	private MockKohaFixture mockKohaFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		patronFixture.deleteAllPatrons();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var hostLms = hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(AGENCY_CODE)
			.name("Koha North")
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLms)
			.build());
		referenceValueMappingFixture.defineLocationToAgencyMapping(KOHA_HOST_LMS_CODE, HOME_BRANCH, AGENCY_CODE);
		referenceValueMappingFixture.definePatronTypeMapping(KOHA_HOST_LMS_CODE, "AD", "DCB", "ADULT");
		mockKohaFixture.mockNoActiveHolds();
	}

	@Test
	void shouldPassAPatronKohaHasNoComplaintAbout() {
		mockKohaFixture.mockPatron(patron().build());

		assertThat(check(), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldRefuseARestrictedPatron() {
		mockKohaFixture.mockPatron(patron().restricted(true).build());

		assertThat(check(), containsInAnyOrder(failedCheck("PATRON_BLOCKED",
			"Patron from \"%s\" has a local account block".formatted(KOHA_HOST_LMS_CODE))));
	}

	@Test
	void shouldRefuseAPatronWhoseCardIsLost() {
		mockKohaFixture.mockPatron(patron().patronCardLost(true).build());

		assertThat(check(), containsInAnyOrder(failedCheck("PATRON_BLOCKED")));
	}

	@Test
	void shouldRefuseAnExpiredPatron() {
		mockKohaFixture.mockPatron(patron().expiryDate("2026-01-31").expired(true).build());

		assertThat(check(), containsInAnyOrder(failedCheck("PATRON_EXPIRED")));
	}

	@Test
	void shouldRefuseAPatronOfACategoryMappedAsIneligible() {
		referenceValueMappingFixture.definePatronTypeMapping(KOHA_HOST_LMS_CODE, "B", "DCB", "NOT_ELIGIBLE");
		mockKohaFixture.mockPatron(patron().categoryId("B").build());

		assertThat(check(), containsInAnyOrder(failedCheck("PATRON_INELIGIBLE")));
	}

	private List<CheckResult> check() {
		return singleValueFrom(check.check(placeRequestCommand("pickup-location",
			KOHA_HOST_LMS_CODE, PATRON_ID, randomUUID())));
	}

	private static KohaPatron.KohaPatronBuilder patron() {
		return KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber("CARD-0001")
			.surname("Example")
			.firstname("Grace")
			.libraryId(HOME_BRANCH)
			.categoryId("AD")
			.expiryDate("2099-12-31")
			.expired(false)
			.restricted(false)
			.patronCardLost(false);
	}
}
