package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowableMonoWrap;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.putAuditData;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class FinaliseRequestTransition implements PatronRequestStateTransition {
	private final PatronRequestAuditService patronRequestAuditService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final BorrowingAgencyService borrowingAgencyService;
	private final PickupAgencyService pickupAgencyService;
	private final CleanupService cleanupService;

	private static final List<Status> possibleSourceStatus = List.of(COMPLETED, CANCELLED);

	public FinaliseRequestTransition(PatronRequestAuditService patronRequestAuditService,
		SupplyingAgencyService supplyingAgencyService, BorrowingAgencyService borrowingAgencyService,
		PickupAgencyService pickupAgencyService, CleanupService cleanupService) {

		this.patronRequestAuditService = patronRequestAuditService;
		this.supplyingAgencyService = supplyingAgencyService;
		this.borrowingAgencyService = borrowingAgencyService;
		this.pickupAgencyService = pickupAgencyService;
		this.cleanupService = cleanupService;
	}

	/**
	 * Attempts to transition the patron request to the next state,
	 *
	 * @param ctx The patron request context to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		PatronRequest patronRequest = ctx.getPatronRequest();
		log.debug("WORKFLOW FinaliseRequestTransition firing for {}", patronRequest);

		return cleanupService.cleanup(ctx)
			.then(Mono.just(patronRequest.setStatus(FINALISED)))
			.flatMap(auditStateOfRecordsAfterCleanUp(ctx));
	}

	private Function<PatronRequest, Mono<RequestWorkflowContext>> auditStateOfRecordsAfterCleanUp(
		RequestWorkflowContext ctx) {

		return patronRequest -> {

			var auditData = new HashMap<String, Object>();

			return fetchVirtualItem(ctx, auditData)
				.flatMap(data -> fetchVirtualPatron(ctx, data))
				.flatMap(data -> fetchVirtualRequest(ctx, data))
				.flatMap(data -> fetchPickupData(ctx, data))
				.flatMap(data -> patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), "Clean up result", data))
				.thenReturn(ctx);
		};
	}

	private Mono<HashMap<String, Object>> fetchPickupData(RequestWorkflowContext ctx,
		HashMap<String, Object> auditData) {

		final var patronRequest = ctx.getPatronRequest();

		if (!patronRequest.isUsingPickupAnywhereWorkflow()) {
			log.debug("Not a PUA workflow, skipping audit of pickup records");
			
			return Mono.just(auditData);
		}
		
		final var pickupSystem = getValueOrNull(ctx, RequestWorkflowContext::getPickupSystem);

		if (pickupSystem == null) {
			log.debug("Unable to audit state of pickup records as there is no pickup system");

			return Mono.just(auditData);
		}

		return pickupAgencyService.getItem(ctx)
			.map(item -> putAuditData(auditData, "PickupItem", getValueOrNull(item, HostLmsItem::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "PickupItem", error))
			.flatMap(data -> pickupAgencyService.getPatron(ctx))
			.map(patron -> putAuditData(auditData, "PickupPatron", getValueOrNull(patron, Patron::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "PickupPatron", error));
	}

	private Mono<HashMap<String, Object>> fetchVirtualRequest(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		final var localRequestId = ctx.getSupplierRequest().getLocalId();
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		final var supplierPatronId = getValueOrNull(supplierRequest, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(localRequestId).localPatronId(supplierPatronId).build();

		return supplyingAgencyService.getRequest(ctx.getSupplierRequest().getHostLmsCode(), hostlmsRequest)
			.map(request -> putAuditData(auditData,"VirtualRequest", getValueOrNull(request, HostLmsRequest::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualRequest", error))
			.thenReturn(auditData);
	}

	private Mono<HashMap<String, Object>> fetchVirtualPatron(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		return supplyingAgencyService.getPatron(ctx)
			.map(patron -> putAuditData(auditData, "VirtualPatron", getValueOrNull(patron, Patron::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualPatron", error))
			.thenReturn(auditData);
	}

	private Mono<HashMap<String, Object>> fetchVirtualItem(RequestWorkflowContext ctx, HashMap<String, Object> auditData) {
		return borrowingAgencyService.getItem(ctx.getPatronRequest())
			.map(item -> putAuditData(auditData, "VirtualItem", getValueOrNull(item, HostLmsItem::toString)))
			.onErrorResume(error -> auditThrowableMonoWrap(auditData, "VirtualItem", error))
			.thenReturn(auditData);
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(FINALISED);
	}

  @Override
	public boolean attemptAutomatically() {
		return true;
	}

  @Override
  public String getName() {
    return "FinaliseRequestTransition";
  }

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("FINALISED", FINALISED.toString()));
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is COMPLETED"));
	}
}
