package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

@Prototype
public class BorrowingAgencyRequestProjector {
	public RequestWorkflowContext apply(
		RequestWorkflowContext context,
		BorrowingAgencyRequestResult result) {

		final var patronRequest = result.patronRequest() != null
			? result.patronRequest()
			: context.getPatronRequest();

		if (patronRequest != null) {
			projectEvidence(patronRequest, result);
			context.setPatronRequest(patronRequest);
		}

		return context;
	}

	private static void projectEvidence(
		PatronRequest patronRequest,
		BorrowingAgencyRequestResult result) {

		patronRequest
			.setLocalRequestId(result.localRequestId())
			.setLocalRequestStatus(result.localRequestStatus())
			.setRawLocalRequestStatus(result.rawLocalRequestStatus())
			.setProtocol(result.protocol())
			.setStatus(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY);

		if (result.createdVirtualBib()) {
			patronRequest.setLocalBibId(result.localBibId());
		}

		if (result.createdVirtualItem()) {
			patronRequest
				.setLocalItemId(result.localItemId())
				.setLocalItemStatus(result.localItemStatus());
		}
	}
}
