package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;

/**
 * Which workflow a request runs under.
 * <p>
 * WorkflowConstants defines RET-LOCAL as "the patron, pickup and lending roles are
 * all within a single system (1 Party)". All three, not two - so the selection has
 * to consider all three, and the shared-system cases are where that starts to
 * matter, because agencies stop being a proxy for systems.
 */
@DcbTest
class WorkflowSelectionTests {
	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	AgencyFixture agencyFixture;

	@Inject
	RequestWorkflowContextHelper requestWorkflowContextHelper;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();

		// One Host LMS serving several libraries - the shared-system shape
		final var sharedSystem = hostLmsFixture.createDummyHostLms("SHARED-SYSTEM");
		agencyFixture.defineAgency("shared-first", "First On Shared", sharedSystem);
		agencyFixture.defineAgency("shared-second", "Second On Shared", sharedSystem);

		// A separate system entirely
		final var otherSystem = hostLmsFixture.createDummyHostLms("OTHER-SYSTEM");
		agencyFixture.defineAgency("other-agency", "Other Agency", otherSystem);

		final var thirdSystem = hostLmsFixture.createDummyHostLms("THIRD-SYSTEM");
		agencyFixture.defineAgency("third-agency", "Third Agency", thirdSystem);
	}

	@Test
	void shouldBeLocalWhenEveryRoleIsTheSameAgency() {
		assertThat(workflowFor("shared-first", "shared-first", "shared-first"),
			is(LOCAL_WORKFLOW));
	}

	@Test
	void shouldBeLocalWhenEveryRoleIsOnOneSharedSystem() {
		// Two libraries, one Koha: the patron borrows from a co-tenant and collects at
		// home. Nothing leaves the system, so DCB places a real hold on the real item
		// and creates no virtual records - which is what makes the sixty-libraries
		// -on-one-Koha case tractable at all.
		assertThat(workflowFor("shared-second", "shared-first", "shared-first"),
			is(LOCAL_WORKFLOW));
	}

	@Test
	void shouldBeExpeditedWhenTheLenderIsAlsoThePickup() {
		assertThat(workflowFor("shared-first", "shared-first", "other-agency"),
			is(EXPEDITED_WORKFLOW));
	}

	@Test
	void shouldBeStandardWhenThePatronCollectsFromTheirOwnLibrary() {
		assertThat(workflowFor("other-agency", "shared-first", "shared-first"),
			is(STANDARD_WORKFLOW));
	}

	@Test
	void shouldBePickupAnywhereWhenAllThreeRolesDiffer() {
		assertThat(workflowFor("shared-first", "other-agency", "third-agency"),
			is(PICKUP_ANYWHERE_WORKFLOW));
	}

	@Test
	void shouldNotBeLocalWhenThePatronIsOnAnotherSystem() {
		// Lender and pickup share a system, but the patron does not. RET-LOCAL would
		// send the request to placeSingularRequest, which resolves its client from the
		// borrowing identity - the patron's system - and hands it the shared system's
		// bib and item ids. They mean nothing there.
		assertThat(workflowFor("shared-second", "shared-first", "other-agency"),
			is(PICKUP_ANYWHERE_WORKFLOW));
	}

	@Test
	void shouldRefuseToLendFromThePatronsOwnLibraryToAnotherPickup() {
		// A standing limitation, recorded here because it bites hardest on a shared
		// system: a patron at one branch of a 60-library Koha borrowing that branch's
		// own copy but collecting at another branch is ordinary behaviour there, and
		// DCB rejects it outright before any system comparison happens.
		final var problem = assertThrows(ThrowableProblem.class,
			() -> workflowFor("shared-first", "shared-second", "shared-first"));

		assertThat(problem.getMessage(),
			containsString("Same supplying and borrowing library, different pickup library"));
	}

	private String workflowFor(String lenderAgencyCode, String pickupAgencyCode,
		String patronAgencyCode) {

		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setLenderAgencyCode(lenderAgencyCode)
			.setLenderAgency(agency(lenderAgencyCode))
			.setPickupAgencyCode(pickupAgencyCode)
			.setPickupAgency(agency(pickupAgencyCode))
			.setPatronAgencyCode(patronAgencyCode)
			.setPatronAgency(agency(patronAgencyCode));

		return requestWorkflowContextHelper.setPatronRequestWorkflow(context)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(PatronRequest::getActiveWorkflow)
			.block();
	}

	private DataAgency agency(String code) {
		return agencyFixture.findByCode(code);
	}
}
