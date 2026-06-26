package org.olf.dcb.request.lifecycle.tracking;

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
public class InboundLifecycleMessageHandler {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final InboundLifecycleMessageIdempotencyGuard idempotencyGuard;

	public InboundLifecycleMessageHandler(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestWorkflowService patronRequestWorkflowService,
		PatronRequestAuditService patronRequestAuditService,
		InboundLifecycleMessageIdempotencyGuard idempotencyGuard) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.idempotencyGuard = idempotencyGuard;
	}

	public Mono<RequestWorkflowContext> handle(InboundLifecycleMessage message) {
		if (!idempotencyGuard.firstSeen(message)) {
			return Mono.empty();
		}

		return Mono.from(patronRequestRepository.findById(
				patronRequestIdFrom(message)))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.flatMap(context -> project(message, context))
			.flatMap(context -> audit(message, context))
			.flatMap(patronRequestWorkflowService::progressUsing);
	}

	private Mono<RequestWorkflowContext> project(
		InboundLifecycleMessage message,
		RequestWorkflowContext context) {

		return switch (message.role()) {
			case SUPPLIER -> projectSupplierMessage(message, context);
			case BORROWER -> projectBorrowerMessage(message, context);
			case PICKUP -> Mono.just(context);
		};
	}

	private Mono<RequestWorkflowContext> projectSupplierMessage(
		InboundLifecycleMessage message,
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();

		if (supplierRequest == null) {
			return Mono.error(new IllegalStateException(
				"Cannot project supplier lifecycle message without supplier request"));
		}

		projectSupplierEvidence(supplierRequest, message);

		return Mono.from(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.map(savedSupplierRequest -> context.setSupplierRequest(savedSupplierRequest));
	}

	private static void projectSupplierEvidence(
		SupplierRequest supplierRequest,
		InboundLifecycleMessage message) {

		supplierRequest
			.setLocalId(message.hostRequestId())
			.setLocalStatus(message.status())
			.setRawLocalStatus(message.rawStatus())
			.setProtocol(message.protocol());

		if (message.itemId() != null) {
			supplierRequest.setLocalItemId(message.itemId());
		}

		if (message.itemBarcode() != null) {
			supplierRequest.setLocalItemBarcode(message.itemBarcode());
		}
	}

	private Mono<RequestWorkflowContext> projectBorrowerMessage(
		InboundLifecycleMessage message,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		projectBorrowerEvidence(patronRequest, message);

		return Mono.from(patronRequestRepository.saveOrUpdate(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private static void projectBorrowerEvidence(
		PatronRequest patronRequest,
		InboundLifecycleMessage message) {

		patronRequest
			.setLocalRequestId(message.hostRequestId())
			.setLocalRequestStatus(message.status())
			.setRawLocalRequestStatus(message.rawStatus())
			.setProtocol(message.protocol());

		if (message.itemId() != null) {
			patronRequest.setLocalItemId(message.itemId());
		}
	}

	private Mono<RequestWorkflowContext> audit(
		InboundLifecycleMessage message,
		RequestWorkflowContext context) {

		final var auditData = new HashMap<String, Object>();
		auditData.put("protocol", message.protocol());
		auditData.put("role", message.role());
		auditData.put("operation", message.operation());
		auditData.put("hostLmsCode", message.hostLmsCode());
		auditData.put("hostRequestId", message.hostRequestId());
		auditData.put("correlationId", message.correlationId());
		auditData.put("status", message.status());
		auditData.put("rawStatus", message.rawStatus());
		auditData.put("messageTimestamp", message.messageTimestamp());
		auditData.put("rawMessageReference", message.rawMessageReference());

		return patronRequestAuditService.addAuditEntry(
				context.getPatronRequest(),
				"Inbound lifecycle message projected.",
				auditData)
			.thenReturn(context);
	}

	private static UUID patronRequestIdFrom(InboundLifecycleMessage message) {
		final var correlationId = message.correlationId();

		if (correlationId == null) {
			throw new IllegalArgumentException(
				"Inbound lifecycle message requires a correlation id");
		}

		final var parts = correlationId.split(":", 2);

		if (parts.length != 2 || !parts[1].equals(message.role().name())) {
			throw new IllegalArgumentException(
				"Inbound lifecycle message correlation id does not match role");
		}

		return UUID.fromString(parts[0]);
	}
}
