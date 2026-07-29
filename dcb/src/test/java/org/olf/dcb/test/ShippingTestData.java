package org.olf.dcb.test;

import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

import java.util.UUID;
import org.olf.dcb.core.interaction.RequestShippingContext;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestShippingContextProjector;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

public final class ShippingTestData {
	private ShippingTestData() {}

	public static RequestShippingContext shippingContext() {
		return RequestShippingContextProjector.project(withShipping(new RequestWorkflowContext()
			.setPatronHomeIdentity(new org.olf.dcb.core.model.PatronIdentity()
				.setLocalId("patron-1")
				.setLocalBarcode("patron-1"))
			.setPatronRequest(new org.olf.dcb.core.model.PatronRequest()
				.setId(UUID.randomUUID()))
			.setSupplierRequest(new SupplierRequest()
				.setHostLmsCode("supplier-host")
				.setLocalAgency("supplier-agency"))));
	}

	public static RequestWorkflowContext withShipping(RequestWorkflowContext context) {
		SupplierRequest supplierRequest = context.getSupplierRequest();
		String supplierSystemCode = supplierRequest != null && supplierRequest.getHostLmsCode() != null
			? supplierRequest.getHostLmsCode() : "supplier-host";
		String supplierAgencyCode = supplierRequest != null && supplierRequest.getLocalAgency() != null
			? supplierRequest.getLocalAgency() : "supplier-agency";
		DataHostLms borrowerSystem = host("borrower-host");
		DataHostLms pickupSystem = host("pickup-host");
		DataHostLms supplierSystem = host(supplierSystemCode);
		DataAgency borrower = agency("borrower-agency", borrowerSystem);
		DataAgency pickup = agency("pickup-agency", pickupSystem);
		DataAgency supplier = agency(supplierAgencyCode, supplierSystem);
		Location location = Location.builder()
			.id(UUID.randomUUID())
			.code("PICKUP-DCB")
			.localId("PICKUP-LOCAL")
			.name("Pickup Library")
			.printLabel("Pickup Library")
			.agency(pickup)
			.hostSystem(pickupSystem)
			.build();
		Library library = Library.builder()
			.id(UUID.randomUUID())
			.agencyCode(pickup.getCode())
			.fullName("Pickup Library")
			.address("1 Test Street")
			.agency(pickup)
			.build();
		if (context.getPatronRequest().getActiveWorkflow() == null) {
			context.getPatronRequest().setActiveWorkflow(STANDARD_WORKFLOW);
		}
		return context
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
		return DataAgency.builder().id(UUID.randomUUID()).code(code).name(code).hostLms(host).build();
	}
}
