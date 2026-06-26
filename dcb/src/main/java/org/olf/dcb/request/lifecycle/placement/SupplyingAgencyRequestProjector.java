package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

@Prototype
public class SupplyingAgencyRequestProjector {
	public RequestWorkflowContext apply(
		RequestWorkflowContext context,
		SupplyingAgencyRequestResult result) {

		if (result.patronRequest() != null) {
			context.setPatronRequest(result.patronRequest());
		}

		if (result.supplierRequest() != null) {
			context.setSupplierRequest(result.supplierRequest());
		}

		return context;
	}
}
