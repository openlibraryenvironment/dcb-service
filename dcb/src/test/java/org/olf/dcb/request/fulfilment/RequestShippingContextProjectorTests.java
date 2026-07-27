package org.olf.dcb.request.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;

class RequestShippingContextProjectorTests {
	@Test
	void projectsDirectPickupWithoutConflatingSupplierShelfLocation() {
		RequestWorkflowContext context = context(STANDARD_WORKFLOW);

		var shipping = RequestShippingContextProjector.project(context);

		assertEquals("Direct", shipping.routeMode());
		assertEquals("BORROWER", shipping.borrowingLibrary().agencyCode());
		assertEquals("PICKUP-MAIN", shipping.pickupDestination().localLocationCode());
		assertEquals("1 Pickup Street", shipping.pickupDestination().address().formatted());
		assertTrue(shipping.shippingInstructions().contains(
			"For pickup by P123@BORROWER-SYSTEM at PICKUP-SYSTEM:PICKUP-MAIN"));
	}

	@Test
	void projectsThreeDistinctPickupAnywhereParties() {
		RequestWorkflowContext context = context(PICKUP_ANYWHERE_WORKFLOW);

		var shipping = RequestShippingContextProjector.project(context);

		assertEquals("PickupAnywhere", shipping.routeMode());
		assertEquals("BORROWER-SYSTEM", shipping.borrowingLibrary().systemCode());
		assertEquals("PICKUP-SYSTEM", shipping.pickupDestination().owner().systemCode());
		assertEquals("SUPPLIER-SYSTEM", shipping.supplier().systemCode());
	}

	@Test
	void rejectsUnresolvedDestination() {
		RequestWorkflowContext context = context(STANDARD_WORKFLOW).setPickupLocation(null);

		IllegalStateException error = assertThrows(IllegalStateException.class,
			() -> RequestShippingContextProjector.project(context));

		assertEquals("Cannot route supplier request: pickup location is missing", error.getMessage());
	}

	// ---- Imperative boundary: a sparse context must never gate placement ----
	//
	// The imperative adapters (Sierra, Polaris, FOLIO, Alma) ignore shipping
	// context entirely, and the workflow deliberately tolerates the two holes
	// exercised below. If projectQuietly ever starts throwing, every imperative
	// supplier placement on a host with sparse pickup or identity data breaks.

	@Test
	void projectQuietlyYieldsNullWhenPickupLocationIsUnresolved() {
		// RequestWorkflowContextHelper returns early without setting a pickup
		// location when the patron request carries no pickup symbol.
		RequestWorkflowContext context = context(STANDARD_WORKFLOW).setPickupLocation(null);

		assertNull(RequestShippingContextProjector.projectQuietly(context));
	}

	@Test
	void projectQuietlyYieldsNullWhenHomeIdentityIsAbsentFromContext() {
		// getRequestingIdentity ends in defaultIfEmpty(ctx), so a context with no
		// home identity is a supported state - SupplyingAgencyService reads the
		// identity from the patron instead.
		RequestWorkflowContext context = context(STANDARD_WORKFLOW).setPatronHomeIdentity(null);

		assertNull(RequestShippingContextProjector.projectQuietly(context));
	}

	@Test
	void projectQuietlyYieldsNullWhenSupplierRequestIsAbsent() {
		RequestWorkflowContext context = context(STANDARD_WORKFLOW).setSupplierRequest(null);

		assertNull(RequestShippingContextProjector.projectQuietly(context));
	}

	@Test
	void projectQuietlyStillProjectsACompleteContext() {
		RequestWorkflowContext context = context(STANDARD_WORKFLOW);

		var shipping = RequestShippingContextProjector.projectQuietly(context);

		assertNotNull(shipping);
		assertEquals("Direct", shipping.routeMode());
		assertEquals("PICKUP-MAIN", shipping.pickupDestination().localLocationCode());
	}

	private static RequestWorkflowContext context(String workflow) {
		DataHostLms borrowerSystem = host("BORROWER-SYSTEM");
		DataHostLms pickupSystem = host("PICKUP-SYSTEM");
		DataHostLms supplierSystem = host("SUPPLIER-SYSTEM");
		DataAgency borrower = agency("BORROWER", borrowerSystem);
		DataAgency pickup = agency("PICKUP", pickupSystem);
		DataAgency supplier = agency("SUPPLIER", supplierSystem);
		Location location = Location.builder()
			.id(UUID.randomUUID())
			.code("PICKUP-DCB-MAIN")
			.localId("PICKUP-MAIN")
			.name("Pickup Main Library")
			.printLabel("Pickup Main")
			.type("Library")
			.agency(pickup)
			.hostSystem(pickupSystem)
			.build();
		Library library = Library.builder()
			.id(UUID.randomUUID())
			.agencyCode("PICKUP")
			.fullName("Pickup Main Library")
			.shortName("Pickup")
			.abbreviatedName("PICKUP")
			.address("1 Pickup Street")
			.agency(pickup)
			.build();
		PatronIdentity identity = PatronIdentity.builder()
			.id(UUID.randomUUID())
			.localId("PATRON-LOCAL")
			.localBarcode("P123")
			.homeIdentity(true)
			.hostLms(borrowerSystem)
			.resolvedAgency(borrower)
			.build();
		PatronRequest request = PatronRequest.builder()
			.id(UUID.randomUUID())
			.pickupLocationCode(location.getId().toString())
			.activeWorkflow(workflow)
			.build();
		return new RequestWorkflowContext()
			.setPatronRequest(request)
			.setSupplierRequest(new org.olf.dcb.core.model.SupplierRequest()
				.setHostLmsCode(supplierSystem.getCode())
				.setLocalAgency(supplier.getCode()))
			.setPatronHomeIdentity(identity)
			.setPatronSystemCode(borrowerSystem.getCode())
			.setPatronAgencyCode(borrower.getCode())
			.setPatronAgency(borrower)
			.setPickupSystemCode(pickupSystem.getCode())
			.setPickupAgencyCode(pickup.getCode())
			.setPickupAgency(pickup)
			.setPickupLocation(location)
			.setPickupLibrary(library)
			.setLenderSystemCode(supplierSystem.getCode())
			.setLenderAgencyCode(supplier.getCode())
			.setLenderAgency(supplier);
	}

	private static DataHostLms host(String code) {
		return DataHostLms.builder().id(UUID.randomUUID()).code(code).name(code).build();
	}

	private static DataAgency agency(String code, DataHostLms host) {
		return DataAgency.builder()
			.id(UUID.randomUUID())
			.code(code)
			.name(code)
			.hostLms(host)
			.build();
	}
}
