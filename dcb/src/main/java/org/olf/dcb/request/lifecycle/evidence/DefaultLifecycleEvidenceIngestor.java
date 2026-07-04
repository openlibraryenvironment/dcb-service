package org.olf.dcb.request.lifecycle.evidence;

import io.micronaut.context.annotation.Prototype;
import java.util.HashMap;
import java.util.UUID;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

@Prototype
public class DefaultLifecycleEvidenceIngestor
	implements LifecycleEvidenceIngestor {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final LifecycleEvidenceIdempotencyGuard idempotencyGuard;

	public DefaultLifecycleEvidenceIngestor(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestWorkflowService patronRequestWorkflowService,
		PatronRequestAuditService patronRequestAuditService,
		LifecycleEvidenceIdempotencyGuard idempotencyGuard) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.idempotencyGuard = idempotencyGuard;
	}

	@Override
	public Mono<RequestWorkflowContext> ingest(LifecycleEvidence evidence) {
		if (!idempotencyGuard.firstSeen(evidence)) {
			return Mono.empty();
		}

		return Mono.from(patronRequestRepository.findById(
				patronRequestIdFrom(evidence)))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.flatMap(context -> project(evidence, context))
			.flatMap(context -> audit(evidence, context))
			.flatMap(patronRequestWorkflowService::progressUsing);
	}

	private Mono<RequestWorkflowContext> project(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		return switch (evidence.role()) {
			case SUPPLIER -> projectSupplierEvidence(evidence, context);
			case BORROWER -> projectBorrowerEvidence(evidence, context);
			case PICKUP -> Mono.just(context);
		};
	}

	private Mono<RequestWorkflowContext> projectSupplierEvidence(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();

		if (supplierRequest == null) {
			return Mono.error(new IllegalStateException(
				"Cannot project supplier lifecycle evidence without supplier request"));
		}

		if (evidence.resource() == LifecycleEvidenceResource.ITEM) {
			projectSupplierItemEvidence(supplierRequest, evidence);
		}
		else {
			projectSupplierRequestEvidence(supplierRequest, evidence);
		}

		return Mono.from(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.map(savedSupplierRequest -> context.setSupplierRequest(savedSupplierRequest));
	}

	private static void projectSupplierRequestEvidence(
		SupplierRequest supplierRequest,
		LifecycleEvidence evidence) {

		supplierRequest
			.setLocalId(evidence.hostRequestId())
			.setLocalStatus(evidence.status())
			.setRawLocalStatus(evidence.rawStatus())
			.setProtocol(evidence.protocol());

		if (evidence.itemId() != null) {
			supplierRequest.setLocalItemId(evidence.itemId());
		}

		if (evidence.itemBarcode() != null) {
			supplierRequest.setLocalItemBarcode(evidence.itemBarcode());
		}
	}

	private static void projectSupplierItemEvidence(
		SupplierRequest supplierRequest,
		LifecycleEvidence evidence) {

		supplierRequest
			.setLocalItemStatus(evidence.status())
			.setRawLocalItemStatus(evidence.rawStatus())
			.setProtocol(evidence.protocol());

		if (evidence.hostRequestId() != null) {
			supplierRequest.setLocalId(evidence.hostRequestId());
		}

		if (evidence.itemId() != null) {
			supplierRequest.setLocalItemId(evidence.itemId());
		}

		if (evidence.itemBarcode() != null) {
			supplierRequest.setLocalItemBarcode(evidence.itemBarcode());
		}
	}

	private Mono<RequestWorkflowContext> projectBorrowerEvidence(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();

		if (evidence.resource() == LifecycleEvidenceResource.ITEM) {
			projectBorrowerItemEvidence(patronRequest, evidence);
		}
		else {
			projectBorrowerRequestEvidence(patronRequest, evidence);
		}

		return Mono.from(patronRequestRepository.saveOrUpdate(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private static void projectBorrowerRequestEvidence(
		PatronRequest patronRequest,
		LifecycleEvidence evidence) {

		patronRequest
			.setLocalRequestId(evidence.hostRequestId())
			.setLocalRequestStatus(evidence.status())
			.setRawLocalRequestStatus(evidence.rawStatus())
			.setProtocol(evidence.protocol());

		if (evidence.itemId() != null) {
			patronRequest.setLocalItemId(evidence.itemId());
		}
	}

	private static void projectBorrowerItemEvidence(
		PatronRequest patronRequest,
		LifecycleEvidence evidence) {

		patronRequest
			.setLocalItemStatus(evidence.status())
			.setRawLocalItemStatus(evidence.rawStatus())
			.setProtocol(evidence.protocol());

		if (evidence.itemId() != null) {
			patronRequest.setLocalItemId(evidence.itemId());
		}
	}

	private Mono<RequestWorkflowContext> audit(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var auditData = new HashMap<String, Object>();
		auditData.put("source", evidence.source().name());
		auditData.put("protocol", evidence.protocol());
		auditData.put("role", evidence.role().name());
		auditData.put("operation", evidence.operation().name());
		auditData.put("resource", evidence.resource().name());
		auditData.put("hostLmsCode", evidence.hostLmsCode());
		auditData.put("hostRequestId", evidence.hostRequestId());
		auditData.put("correlationId", evidence.correlationId());
		auditData.put("status", evidence.status());
		auditData.put("rawStatus", evidence.rawStatus());
		auditData.put("itemId", evidence.itemId());
		auditData.put("itemBarcode", evidence.itemBarcode());
		auditData.put("messageTimestamp", evidence.messageTimestamp());
		auditData.put("rawMessageReference", evidence.rawMessageReference());

		return patronRequestAuditService.addAuditEntry(
				context.getPatronRequest(),
				"Inbound lifecycle message projected.",
				auditData)
			.thenReturn(context);
	}

	private static UUID patronRequestIdFrom(LifecycleEvidence evidence) {
		final var correlationId = evidence.correlationId();

		if (correlationId == null) {
			throw new IllegalArgumentException(
				"Lifecycle evidence requires a correlation id");
		}

		final var parts = correlationId.split(":", 2);

		if (parts.length != 2 || !parts[1].equals(evidence.role().name())) {
			throw new IllegalArgumentException(
				"Lifecycle evidence correlation id does not match role");
		}

		return UUID.fromString(parts[0]);
	}
}
