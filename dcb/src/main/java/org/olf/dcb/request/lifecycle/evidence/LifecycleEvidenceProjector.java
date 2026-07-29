package org.olf.dcb.request.lifecycle.evidence;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import reactor.core.publisher.Mono;

public interface LifecycleEvidenceProjector {
	Mono<RequestWorkflowContext> project(LifecycleEvidence evidence);

	/**
	 * Project evidence onto entity instances the caller already holds.
	 *
	 * Tracking discovers a state change by polling with a PatronRequest / SupplierRequest
	 * already loaded into its RequestWorkflowContext, and continues to use that same instance
	 * after projection - it evaluates workflow transitions against it and then saves it when
	 * scheduling the next check. Re-reading the row here would produce a second instance, so
	 * the caller's copy would neither see the projected status nor stop overwriting it.
	 *
	 * Pass the live instances in via the seed to have them mutated in place. Callers with no
	 * entity in hand (inbound protocol messages) use the single argument form, which loads them.
	 */
	Mono<RequestWorkflowContext> project(LifecycleEvidence evidence,
		RequestWorkflowContext seed);
}
