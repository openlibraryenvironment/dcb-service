package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

@Prototype
public class BorrowingAgencyRequestProjector {
	public RequestWorkflowContext apply(
		RequestWorkflowContext context,
		BorrowingAgencyRequestResult result) {

		if (result.patronRequest() != null) {
			context.setPatronRequest(result.patronRequest());
		}

		return context;
	}
}
