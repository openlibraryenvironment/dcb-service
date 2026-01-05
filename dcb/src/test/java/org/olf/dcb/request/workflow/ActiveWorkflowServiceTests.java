package org.olf.dcb.request.workflow;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasTitle;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

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
	private HostLmsFixture hostLmsFixture;
	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
		locationFixture.deleteAll();
	}

	@Test
	void shouldBeStandardWorkflowWhenOnlySupplyingLibraryIsDifferent() {
		// Arrange
		final var borrowingAndPickupHostLms = hostLmsFixture.createDummyHostLms("borrowing-and-pickup-host-lms");

		final var borrowingAndPickupAgency = agencyFixture.defineAgency("borrowing-and-pickup-agency",
			"Borrowing and Pickup Agency", borrowingAndPickupHostLms);

		final var pickupLocation = locationFixture.createPickupLocation(
			borrowingAndPickupAgency);

		final var pickupLocationId = getValueOrNull(
			pickupLocation, Location::getId, UUID::toString);

		final var supplyingHostLms = hostLmsFixture.createDummyHostLms("supplying-host-lms");

		final var supplyingAgency = agencyFixture.defineAgency("supplying-agency",
			"Supplying Agency", supplyingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAgency,
			borrowingAndPickupAgency, pickupLocationId);

		// Assert
		assertThat(determinedWorkflow, is(STANDARD_WORKFLOW));
	}


	@Test
	void shouldBeLocalWorkflowWhenSameLibraryProvidesAllRoles() {
		// Arrange
		final var allRolesLibraryHostLms = hostLmsFixture.createDummyHostLms("all-roles-host-lms");

		final var allRolesAgency = agencyFixture.defineAgency("all-roles-agency",
			"All Roles Agency", allRolesLibraryHostLms);

		final var pickupLocation = locationFixture.createPickupLocation(
			allRolesAgency);

		final var pickupLocationId = getValueOrNull(
			pickupLocation, Location::getId, UUID::toString);

		// Act
		final var determinedWorkflow = determineWorkflow(allRolesAgency,
			allRolesAgency, pickupLocationId);

		// Assert
		assertThat(determinedWorkflow, is(LOCAL_WORKFLOW));
	}

	@Test
	void shouldBeExpeditedWorkflowWhenOnlyBorrowingLibraryIsDifferent() {
		// Arrange
		final var supplyingAndPickupHostLms = hostLmsFixture.createDummyHostLms("supplying-and-pickup-host-lms");

		final var supplyingAndPickupAgency = agencyFixture.defineAgency("supplying-and-pickup-agency",
			"Supplying and Pickup Agency", supplyingAndPickupHostLms);

		final var pickupLocation = locationFixture.createPickupLocation(supplyingAndPickupAgency);

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId, UUID::toString);

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("borrowing-host-lms");

		final var borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAndPickupAgency,
			borrowingAgency, pickupLocationId);

		// Assert
		assertThat(determinedWorkflow, is(EXPEDITED_WORKFLOW));
	}

	@Test
	void shouldBePickupAnywhereWorkflowWhenEachRoleIsProvidedByDifferentLibrary() {
		// Arrange
		final var pickupHostLms = hostLmsFixture.createDummyHostLms("pickup-host-lms");

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency",
			"Pickup Agency", pickupHostLms);

		final var pickupLocation = locationFixture.createPickupLocation(pickupAgency);

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId, UUID::toString);

		final var borrowingHostLms = hostLmsFixture.createDummyHostLms("borrowing-host-lms");

		final var borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);

		final var supplyingHostLms = hostLmsFixture.createDummyHostLms("supplying-host-lms");

		final var supplyingAgency = agencyFixture.defineAgency("supplying-agency",
			"Supplying Agency", supplyingHostLms);

		// Act
		final var determinedWorkflow = determineWorkflow(supplyingAgency,
			borrowingAgency, pickupLocationId);

		// Assert
		assertThat(determinedWorkflow, is(PICKUP_ANYWHERE_WORKFLOW));
	}

	@Test
	void shouldFailWhenSameSupplyingAndBorrowingYetDifferentPickupLibraryAsIsUnsupported() {
		// Arrange
		final var pickupHostLms = hostLmsFixture.createDummyHostLms("pickup-host-lms");

		final var pickupAgency = agencyFixture.defineAgency("pickup-agency",
			"Pickup Agency", pickupHostLms);

		final var pickupLocation = locationFixture.createPickupLocation(pickupAgency);

		final var pickupLocationId = getValueOrNull(pickupLocation, Location::getId, UUID::toString);

		final var supplyingAndBorrowingHostLms = hostLmsFixture.createDummyHostLms(
			"supplying-and-borrowing-host-lms");

		final var supplyingAndBorrowingAgency = agencyFixture.defineAgency(
			"supplying-and-borrowing-agency", "Supplying And Borrowing Agency",
			supplyingAndBorrowingHostLms);

		// Act
		final var error = assertThrows(UnsupportedWorkflowProblem.class,
			() -> determineWorkflow(supplyingAndBorrowingAgency,
				supplyingAndBorrowingAgency, pickupLocationId));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasTitle("Unsupported workflow: Same supplying and borrowing library, different pickup library")
		));
	}

	private String determineWorkflow(DataAgency supplyingAgency,
		DataAgency borrowingAgency, String pickupLocationId) {

		String supplyingAgencyCode = getValueOrNull(supplyingAgency, DataAgency::getCode);
		String borrowingAgencyCode = getValueOrNull(borrowingAgency, DataAgency::getCode);

		final var patronRequest = PatronRequest.builder()
			.pickupLocationCode(pickupLocationId)
			.build();

		return singleValueFrom(activeWorkflowService.updatePatronRequest(patronRequest,
				borrowingAgencyCode, supplyingAgencyCode)
			.map(PatronRequest::getActiveWorkflow));
	}
}
