package org.olf.dcb.request.lifecycle.evidence;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import reactor.core.publisher.Mono;

@Prototype
public class DefaultLifecycleEvidenceIngestor
	implements LifecycleEvidenceIngestor {
	private final LifecycleEvidenceProjector lifecycleEvidenceProjector;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final LifecycleEvidenceIdempotencyGuard idempotencyGuard;

	public DefaultLifecycleEvidenceIngestor(
		LifecycleEvidenceProjector lifecycleEvidenceProjector,
		PatronRequestWorkflowService patronRequestWorkflowService,
		LifecycleEvidenceIdempotencyGuard idempotencyGuard) {

		this.lifecycleEvidenceProjector = lifecycleEvidenceProjector;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.idempotencyGuard = idempotencyGuard;
	}

	@Override
	public Mono<RequestWorkflowContext> ingest(LifecycleEvidence evidence) {
		if (!idempotencyGuard.firstSeen(evidence)) {
			return Mono.empty();
		}

		return lifecycleEvidenceProjector.project(evidence)
			.flatMap(patronRequestWorkflowService::progressUsing);
	}
}
