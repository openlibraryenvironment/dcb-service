package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

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

		final var localId = patronRequest.getLocalRequestId();
		final var localItemId = patronRequest.getLocalItemId();

		return hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(localItemId, HostLmsClient.CanonicalItemState.COMPLETED, localId));
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

		ctx.getPatronRequest().setStatus(PatronRequest.Status.COMPLETED);

		// DCB-851 update borrowing lib
		return updateBorrowerThatItemHasBeenReceivedBack(ctx.getPatronRequest())
			.flatMap( ok -> Mono.from(supplierRequestRepository.saveOrUpdate(ctx.getSupplierRequest()) ) )
			.flatMap( ssr -> Mono.from(patronRequestRepository.saveOrUpdate(ctx.getPatronRequest())) )
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
