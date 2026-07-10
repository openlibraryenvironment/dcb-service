package org.olf.dcb.request.lifecycle.evidence;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import reactor.core.publisher.Mono;

public interface LifecycleEvidenceProjector {
	Mono<RequestWorkflowContext> project(LifecycleEvidence evidence);
}
