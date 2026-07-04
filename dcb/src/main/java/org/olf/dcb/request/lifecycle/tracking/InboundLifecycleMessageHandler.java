package org.olf.dcb.request.lifecycle.tracking;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidence;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceIngestor;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceSource;
import reactor.core.publisher.Mono;

@Prototype
public class InboundLifecycleMessageHandler {
	private final LifecycleEvidenceIngestor lifecycleEvidenceIngestor;

	public InboundLifecycleMessageHandler(
		LifecycleEvidenceIngestor lifecycleEvidenceIngestor) {

		this.lifecycleEvidenceIngestor = lifecycleEvidenceIngestor;
	}

	public Mono<RequestWorkflowContext> handle(InboundLifecycleMessage message) {
		return lifecycleEvidenceIngestor.ingest(new LifecycleEvidence(
			LifecycleEvidenceSource.INBOUND_PROTOCOL,
			message.protocol(),
			message.role(),
			message.operation(),
			message.resource(),
			message.hostLmsCode(),
			message.hostRequestId(),
			message.correlationId(),
			message.status(),
			message.rawStatus(),
			message.itemId(),
			message.itemBarcode(),
			message.messageTimestamp(),
			message.rawMessageReference()));
	}
}
