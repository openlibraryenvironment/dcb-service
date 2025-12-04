package org.olf.dcb.request.workflow;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class ActiveWorkflowServiceTests {
	@Inject
	private ActiveWorkflowService activeWorkflowService;

	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldBeStandardWorkflowWhenOnlySupplyingLibraryIsDifferent() {
		// Arrange
		final var borrowingAndPickupHostLms = hostLmsFixture.createDummyHostLms("borrowing-and-pickup-host-lms");

		final var borrowingAndPickupAgency = agencyFixture.defineAgency("borrowing-and-pickup-agency",
			"Borrowing and Pickup Agency", borrowingAndPickupHostLms);

		final var pickupLocationCode = "pickup-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			borrowingAndPickupHostLms.getCode(), pickupLocationCode, borrowingAndPickupAgency.getCode());

		final var supplyingHostLms = hostLmsFixture.createDummyHostLms("supplying-host-lms");

		final var supplyingAgency = agencyFixture.defineAgency("supplying-agency",
			"Supplying Agency", supplyingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAgency.getCode(),
			borrowingAndPickupAgency.getCode(), pickupLocationCode, borrowingAndPickupHostLms.getCode());

		// Assert
		assertThat(determinedWorkflow, is(STANDARD_WORKFLOW));
	}


	@Test
	void shouldBeLocalWorkflowWhenSameLibraryProvidesAllRoles() {
		// Arrange
		final var allRolesLibraryHostLms = hostLmsFixture.createDummyHostLms("all-roles-host-lms");

		final var allRolesAgency = agencyFixture.defineAgency("all-roles-agency",
			"All Roles Agency", allRolesLibraryHostLms);

		final var pickupLocationCode = "pickup-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			allRolesLibraryHostLms.getCode(), pickupLocationCode, allRolesAgency.getCode());

		// Act
		final var determinedWorkflow = determineWorkflow(allRolesAgency.getCode(),
			allRolesAgency.getCode(), pickupLocationCode, allRolesLibraryHostLms.getCode());

		// Assert
		assertThat(determinedWorkflow, is(LOCAL_WORKFLOW));
	}

	@Test
	void shouldBeExpeditedWorkflowWhenOnlyBorrowingLibraryIsDifferent() {
		// Arrange
		final var supplyingAndPickupHostLms = hostLmsFixture.createDummyHostLms("supplying-and-pickup-host-lms");

		final var supplyingAndPickupAgency = agencyFixture.defineAgency("supplying-and-pickup-agency",
			"Supplying and Pickup Agency", supplyingAndPickupHostLms);

		final var pickupLocationCode = "pickup-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			supplyingAndPickupHostLms.getCode(), pickupLocationCode, supplyingAndPickupAgency.getCode());

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("borrowing-host-lms");

		final var borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAndPickupAgency.getCode(),
			borrowingAgency.getCode(), pickupLocationCode, supplyingAndPickupHostLms.getCode());

		// Assert
		assertThat(determinedWorkflow, is(EXPEDITED_WORKFLOW));
	}

	@Test
	void shouldBePickupAnywhereWorkflowWhenEachRoleIsProvidedByDifferentLibrary() {
		// Arrange
		final var pickupHostLms = hostLmsFixture.createDummyHostLms("pickup-host-lms");

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency",
			"Pickup Agency", pickupHostLms);

		final var pickupLocationCode = "pickup-location";

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			pickupHostLms.getCode(), pickupLocationCode, pickupAgency.getCode());

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("borrowing-host-lms");

		final var borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		final var supplyingHostLms = hostLmsFixture.createDummyHostLms("supplying-host-lms");

		final var supplyingAgency = agencyFixture.defineAgency("supplying-agency",
			"Supplying Agency", supplyingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAgency.getCode(),
			borrowingAgency.getCode(), pickupLocationCode, pickupHostLms.getCode());

		// Assert
		assertThat(determinedWorkflow, is(PICKUP_ANYWHERE_WORKFLOW));
	}

	private String determineWorkflow(String supplyingAgencyCode,
		String borrowingAgencyCode, String pickupLocationCode, String pickupLocationContext) {

		final var patronRequest = PatronRequest.builder()
			.pickupLocationCode(pickupLocationCode)
			.pickupLocationCodeContext(pickupLocationContext)
			.build();

		return singleValueFrom(activeWorkflowService.updatePatronRequest(patronRequest,
				borrowingAgencyCode, supplyingAgencyCode)
			.map(PatronRequest::getActiveWorkflow));
	}
}
