package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Slf4j
@Singleton
@Named("SupplierRequestItemAvailable")
public class HandleSupplierItemAvailable implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService auditService;
  private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.RETURN_TRANSIT);
	
	public HandleSupplierItemAvailable(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		PatronRequestAuditService auditService,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.auditService = auditService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.hostLmsService = hostLmsService;
	}

	private Mono<String> updateBorrowerThatItemHasBeenReceivedBack(PatronRequest patronRequest) {

		final var localRequestId = getValueOrNull(patronRequest, PatronRequest::getLocalRequestId);
		final var localItemId = getValueOrNull(patronRequest, PatronRequest::getLocalItemId);
		final var localBibId = getValueOrNull(patronRequest, PatronRequest::getLocalBibId);
		final var localHoldingsId = getValueOrNull(patronRequest, PatronRequest::getLocalHoldingId);
		final var hostLmsItem = HostLmsItem.builder()
			.localId(localItemId)
			.bibId(localBibId)
			.holdingId(localHoldingsId)
			.localRequestId(localRequestId)
			.build();

		return hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(hostLmsItem,
				HostLmsClient.CanonicalItemState.COMPLETED))
			// Best-effort borrower notification. Completion is authoritatively gated by the supplier
			// having the item back; poking the borrower's virtual item is secondary cleanup that
			// finalisation completes regardless (and FOLIO deleteHold already tolerates the same case).
			// It legitimately fails when the borrower side is already terminal - e.g. a FOLIO borrower
			// whose mod-dcb transaction is CANCELLED after a patron cancellation, where CANCELLED -> CLOSED
			// is not a permitted mod-dcb transition. Do not let that wedge the request in RETURN_TRANSIT.
			.onErrorResume(error -> {
				log.warn("Best-effort borrower 'item received back' update failed for {} - continuing to COMPLETED: {}",
					getValueOrNull(patronRequest, PatronRequest::getId), error.getMessage());

				return auditService.addAuditEntry(patronRequest,
						"Supplier item available - borrower 'received back' update skipped (borrower already terminal): "
							+ error.getMessage())
					.thenReturn("OK");
			});
	}


  @Override
  public boolean isApplicableFor(RequestWorkflowContext ctx) {
    return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
      ( ctx.getSupplierRequest() != null ) &&
			// N.B. CLOSED is somewhat FOLIO specific - should be revisited soon
			// Received is a sierra state
      ( 
				(HostLmsItem.ITEM_RECEIVED.equals(ctx.getSupplierRequest().getLocalItemStatus())) || 
				(HostLmsItem.ITEM_AVAILABLE.equals(ctx.getSupplierRequest().getLocalItemStatus())) ||
				( ("CLOSED".equals(ctx.getSupplierRequest().getLocalStatus() ) ) ) 
			);
  }

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		// DCB-851 update borrowing lib
		return updateBorrowerThatItemHasBeenReceivedBack(ctx.getPatronRequest())
			.map( ok -> ctx.getPatronRequest().setStatus(PatronRequest.Status.COMPLETED))
			.doOnSuccess( pr -> Mono.from(supplierRequestRepository.saveOrUpdate(ctx.getSupplierRequest()) ) )
			.flatMap( pr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)) )
			.flatMap( spr -> auditService.addAuditEntry(spr, "Supplier Item Available - Infers item back on the shelf after loan. Completing request") )
			.thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.COMPLETED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierItemAvailable";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of( new DCBGuardCondition("DCBPatronRequest state is RETURN_TRANSIT and Supplier item status is AVAILABLE"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of( new DCBTransitionResult("AVAILABLE",PatronRequest.Status.COMPLETED.toString()));
	}
}
