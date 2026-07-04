package org.olf.dcb.request.lifecycle.evidence;

import io.micronaut.context.annotation.Prototype;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

@Prototype
public class DefaultLifecycleEvidenceProjector
	implements LifecycleEvidenceProjector {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestAuditService patronRequestAuditService;

	public DefaultLifecycleEvidenceProjector(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public Mono<RequestWorkflowContext> project(LifecycleEvidence evidence) {
		return Mono.from(patronRequestRepository.findById(
				patronRequestIdFrom(evidence)))
			.flatMap(patronRequest -> contextFor(evidence, patronRequest))
			.flatMap(context -> project(evidence, context))
			.flatMap(context -> audit(evidence, context));
	}

	private Mono<RequestWorkflowContext> contextFor(
		LifecycleEvidence evidence,
		PatronRequest patronRequest) {

		if (evidence.source() == LifecycleEvidenceSource.POLLING) {
			return Mono.just(new RequestWorkflowContext()
				.setPatronRequest(patronRequest));
		}

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest);
	}

	private Mono<RequestWorkflowContext> project(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		if (evidence.source() == LifecycleEvidenceSource.POLLING) {
			return projectPollingEvidence(evidence, context);
		}

		return switch (evidence.role()) {
			case SUPPLIER -> projectSupplierEvidence(evidence, context);
			case BORROWER -> projectBorrowerEvidence(evidence, context);
			case PICKUP -> Mono.just(context);
		};
	}

	private Mono<RequestWorkflowContext> projectPollingEvidence(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		return switch (evidence.trackingResourceType()) {
			case "SupplierRequest" -> projectPolledSupplierRequest(evidence, context);
			case "PatronRequest" -> projectPolledPatronRequest(evidence, context);
			case "BorrowerVirtualItem" -> projectPolledBorrowerVirtualItem(evidence, context);
			case "SupplierItem" -> projectPolledSupplierItem(evidence, context);
			case "PickupRequest" -> projectPolledPickupRequest(evidence, context);
			case "PickupItem" -> projectPolledPickupItem(evidence, context);
			default -> Mono.error(new IllegalArgumentException(
				"Polling lifecycle evidence for unknown resource type "
					+ evidence.trackingResourceType()));
		};
	}

	private Mono<RequestWorkflowContext> projectPolledSupplierRequest(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		return supplierRequestFor(evidence, context)
			.flatMap(supplierRequest -> {
				supplierRequest.setLocalStatus(evidence.status());
				supplierRequest.setLocalRequestLastCheckTimestamp(Instant.now());
				supplierRequest.setLocalRequestStatusRepeat(Long.valueOf(0));

				return Mono.from(supplierRequestRepository.update(supplierRequest))
					.map(savedSupplierRequest -> context.setSupplierRequest(savedSupplierRequest));
			});
	}

	private Mono<RequestWorkflowContext> projectPolledPatronRequest(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		patronRequest.setLocalRequestStatus(evidence.status());
		patronRequest.setLocalRequestLastCheckTimestamp(Instant.now());
		patronRequest.setLocalRequestStatusRepeat(Long.valueOf(0));

		return Mono.from(patronRequestRepository.update(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private Mono<RequestWorkflowContext> projectPolledBorrowerVirtualItem(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		patronRequest.setLocalItemStatus(evidence.status());
		patronRequest.setLocalItemLastCheckTimestamp(Instant.now());
		patronRequest.setLocalItemStatusRepeat(Long.valueOf(0));
		patronRequest.setLocalRenewalCount(evidence.toRenewalCount());

		return Mono.from(patronRequestRepository.update(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private Mono<RequestWorkflowContext> projectPolledSupplierItem(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		return supplierRequestFor(evidence, context)
			.flatMap(supplierRequest -> {
				supplierRequest.setLocalItemStatus(evidence.status());
				supplierRequest.setLocalItemLastCheckTimestamp(Instant.now());
				supplierRequest.setLocalItemStatusRepeat(Long.valueOf(0));
				supplierRequest.setLocalRenewalCount(evidence.toRenewalCount());
				supplierRequest.setLocalHoldCount(evidence.toHoldCount());
				supplierRequest.setLocalRenewable(evidence.renewable());

				return Mono.from(supplierRequestRepository.update(supplierRequest))
					.map(savedSupplierRequest -> context.setSupplierRequest(savedSupplierRequest));
			});
	}

	private Mono<RequestWorkflowContext> projectPolledPickupRequest(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		patronRequest.setPickupRequestStatus(evidence.status());
		patronRequest.setPickupRequestLastCheckTimestamp(Instant.now());
		patronRequest.setPickupRequestStatusRepeat(Long.valueOf(0));

		return Mono.from(patronRequestRepository.update(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private Mono<RequestWorkflowContext> projectPolledPickupItem(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		patronRequest.setPickupItemStatus(evidence.status());
		patronRequest.setPickupItemLastCheckTimestamp(Instant.now());
		patronRequest.setPickupItemStatusRepeat(Long.valueOf(0));

		return Mono.from(patronRequestRepository.update(patronRequest))
			.map(savedPatronRequest -> context.setPatronRequest(savedPatronRequest));
	}

	private Mono<RequestWorkflowContext> projectSupplierEvidence(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var supplierRequest = requiredSupplierRequest(context);

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
			.setRawLocalStatus(evidence.rawStatus());

		setProtocolIfPresent(supplierRequest, evidence);

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
			.setRawLocalItemStatus(evidence.rawStatus());

		setProtocolIfPresent(supplierRequest, evidence);

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
			.setRawLocalRequestStatus(evidence.rawStatus());

		setProtocolIfPresent(patronRequest, evidence);

		if (evidence.itemId() != null) {
			patronRequest.setLocalItemId(evidence.itemId());
		}
	}

	private static void projectBorrowerItemEvidence(
		PatronRequest patronRequest,
		LifecycleEvidence evidence) {

		patronRequest
			.setLocalItemStatus(evidence.status())
			.setRawLocalItemStatus(evidence.rawStatus());

		setProtocolIfPresent(patronRequest, evidence);

		if (evidence.itemId() != null) {
			patronRequest.setLocalItemId(evidence.itemId());
		}
	}

	private Mono<RequestWorkflowContext> audit(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		if (evidence.source() == LifecycleEvidenceSource.POLLING
			&& evidence.trackingResourceType() != null) {

			return auditPollingEvidence(evidence, context);
		}

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

	private Mono<RequestWorkflowContext> auditPollingEvidence(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		final var msg = "to %s from %s - %s(%s)".formatted(
			evidence.status(), evidence.fromStatus(),
			evidence.trackingResourceType(), evidence.trackingResourceId());

		final var auditData = new HashMap<String, Object>();
		auditData.put("source", evidence.source().name());
		auditData.put("role", evidence.role().name());
		auditData.put("resource", evidence.resource().name());
		auditData.put("patronRequestId", patronRequestIdFrom(evidence));
		auditData.put("resourceType", evidence.trackingResourceType());
		auditData.put("resourceId", evidence.trackingResourceId());
		auditData.put("fromState", evidence.fromStatus());
		auditData.put("toState", evidence.status());

		if (evidence.fromRenewalCount() != null) {
			auditData.put("fromRenewalCount", evidence.fromRenewalCount());
		}
		if (evidence.toRenewalCount() != null) {
			auditData.put("toRenewalCount", evidence.toRenewalCount());
		}
		if ("SupplierItem".equals(evidence.trackingResourceType())) {
			auditData.put("fromLocalHoldCount", evidence.fromHoldCount());
			auditData.put("toLocalHoldCount", evidence.toHoldCount());
		}
		if (evidence.trackingProperties() != null) {
			auditData.putAll(evidence.trackingProperties());
		}

		return patronRequestAuditService.addAuditEntry(
				context.getPatronRequest().getId(), msg, auditData)
			.thenReturn(context);
	}

	private static SupplierRequest requiredSupplierRequest(
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();

		if (supplierRequest == null) {
			throw new IllegalStateException(
				"Cannot project supplier lifecycle evidence without supplier request");
		}

		return supplierRequest;
	}

	private Mono<SupplierRequest> supplierRequestFor(
		LifecycleEvidence evidence,
		RequestWorkflowContext context) {

		if (context.getSupplierRequest() != null) {
			return Mono.just(context.getSupplierRequest());
		}

		if (evidence.trackingResourceId() == null) {
			return Mono.error(new IllegalStateException(
				"Cannot project supplier lifecycle evidence without supplier request"));
		}

		return Mono.from(supplierRequestRepository.findById(
				UUID.fromString(evidence.trackingResourceId())))
			.switchIfEmpty(Mono.error(new IllegalStateException(
				"Cannot find supplier request for lifecycle evidence "
					+ evidence.trackingResourceId())));
	}

	private static void setProtocolIfPresent(
		SupplierRequest supplierRequest,
		LifecycleEvidence evidence) {

		if (evidence.protocol() != null) {
			supplierRequest.setProtocol(evidence.protocol());
		}
	}

	private static void setProtocolIfPresent(
		PatronRequest patronRequest,
		LifecycleEvidence evidence) {

		if (evidence.protocol() != null) {
			patronRequest.setProtocol(evidence.protocol());
		}
	}

	static UUID patronRequestIdFrom(LifecycleEvidence evidence) {
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
