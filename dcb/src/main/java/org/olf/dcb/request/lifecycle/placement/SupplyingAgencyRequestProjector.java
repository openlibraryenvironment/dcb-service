package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

@Prototype
public class SupplyingAgencyRequestProjector {
	public RequestWorkflowContext apply(
		RequestWorkflowContext context,
		SupplyingAgencyRequestResult result) {

		final var patronRequest = result.patronRequest() != null
			? result.patronRequest()
			: context.getPatronRequest();

		if (patronRequest != null) {
			patronRequest.setStatus(
				PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);
			context.setPatronRequest(patronRequest);
		}

		final var supplierRequest = result.supplierRequest() != null
			? result.supplierRequest()
			: context.getSupplierRequest();

		if (supplierRequest != null) {
			projectEvidence(supplierRequest, result);
			context.setSupplierRequest(supplierRequest);
		}

		return context;
	}

	private static void projectEvidence(
		SupplierRequest supplierRequest,
		SupplyingAgencyRequestResult result) {

		supplierRequest.placed(
			result.localRequestId(),
			result.localRequestStatus(),
			result.rawLocalRequestStatus(),
			result.localItemId(),
			result.localItemBarcode());
	}
}
