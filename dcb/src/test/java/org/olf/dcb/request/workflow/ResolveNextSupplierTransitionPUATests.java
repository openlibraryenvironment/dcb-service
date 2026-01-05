package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasNoResolutionCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ResolveNextSupplierTransitionPUATests {
	@Inject private PatronFixture patronFixture;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private SupplierRequestsFixture supplierRequestsFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	@Inject private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject private ResolveNextSupplierTransition resolveNextSupplierTransition;
	@Inject private LocationFixture locationFixture;

	private static final String BORROWING_HOST_LMS_CODE = "borrowing-host-lms-code";
	private static final String PICKUP_HOST_LMS_CODE = "pickup-host-lms-code";

	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;
	private DataHostLms pickupHostLms;
	private DataAgency pickupAgency;

	@BeforeAll
	void beforeAll() {
		locationFixture.deleteAll();
		hostLmsFixture.deleteAll();

		// Create dummy host LMS instances
		borrowingHostLms = createHostLms(BORROWING_HOST_LMS_CODE);
		pickupHostLms = createHostLms(PICKUP_HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		consortiumFixture.deleteAll();
		locationFixture.deleteAll();

		// Define agencies
		borrowingAgency = defineAgency("borrowing-agency", "Borrowing Agency", borrowingHostLms);
		pickupAgency = defineAgency(PICKUP_HOST_LMS_CODE, "Pickup Agency", pickupHostLms);
	}

	/**
	 * Verifies that Pickup Anywhere (RET-PUA) requests are terminated when re-resolution is not required.
	 * This test case ensures that when the ResolveNextSupplierTransition is executed and the RET-PUA workflow is active,
	 * hold requests are cancelled at both the borrowing system and the pickup system, as expected.
	 *
	 */
	@Test
	void shouldTerminatePUARequestsWhenReResolutionIsNotRequired() {
		// Arrange
		final var borrowingLocalRequestId = "7836734";
		final var pickupLocalRequestId = "8367347";

		final var patron = createPatron("783174", "home-library",
			borrowingHostLms, borrowingAgency);

		final var pickupLocationId = createPickupLocation(pickupAgency);

		final var patronRequest = createPatronRequest(patron, borrowingLocalRequestId,
			pickupLocalRequestId, pickupLocationId);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY),
			hasNoResolutionCount()
		));
	}

	private PatronRequest resolveNextSupplier(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!resolveNextSupplierTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Resolve next supplier is not applicable for request"));
				}

				return resolveNextSupplierTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}

	// Helper methods

	private void defineSupplierRequest(PatronRequest patronRequest, String localStatus) {
		supplierRequestsFixture.saveSupplierRequest(createSupplierRequest(patronRequest, localStatus));
	}

	private DataHostLms createHostLms(String code) {
		return hostLmsFixture.createDummyHostLms(code);
	}

	private DataAgency defineAgency(String code, String name, DataHostLms hostLms) {
		return agencyFixture.defineAgency(code, name, hostLms);
	}

	private Patron createPatron(String id, String homeLibrary, DataHostLms hostLms, DataAgency agency) {
		return patronFixture.definePatron(id, homeLibrary, hostLms, agency);
	}

	private String createPickupLocation(DataAgency agency) {
		final var pickupLocation = locationFixture.createPickupLocation(
			agency);

		return getValueOrNull(pickupLocation, Location::getId, UUID::toString);
	}

	private PatronRequest createPatronRequest(Patron patron,
		String borrowingLocalRequestId, String pickupLocalRequestId,
		String pickupLocationId) {

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.pickupRequestId(pickupLocalRequestId)
			.pickupLocationCode(pickupLocationId)
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private SupplierRequest createSupplierRequest(PatronRequest patronRequest, String localStatus) {
		return SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("next-supplier-supplying-host-lms")
			.localItemId("48375735")
			.localStatus(localStatus)
			.build();
	}
}
