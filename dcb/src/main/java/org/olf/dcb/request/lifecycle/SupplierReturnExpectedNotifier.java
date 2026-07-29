package org.olf.dcb.request.lifecycle;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import reactor.core.publisher.Mono;

/** Notifies a supplier that an item is on its return leg. */
public interface SupplierReturnExpectedNotifier {
	Mono<RequestWorkflowContext> notifyExpectedReturn(
		RequestWorkflowContext context);
}
