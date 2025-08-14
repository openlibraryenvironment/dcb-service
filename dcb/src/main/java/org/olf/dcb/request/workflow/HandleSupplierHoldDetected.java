package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
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

/**
 * Back at the owning institution, a patron has placed a hold on a loaned item
 */
@Slf4j
@Singleton
@Named("SupplierHoldDetected")
public class HandleSupplierHoldDetected implements PatronRequestStateTransition {

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService auditService;
  private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.LOANED);
	
	public HandleSupplierHoldDetected(PatronRequestRepository patronRequestRepository,
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

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {

		return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
			( ctx.getSupplierRequest() != null ) &&
			( 
				( shouldPreventRenewal(ctx) ) &&
				(
					(ctx.getPatronRequest().getRenewalStatus() == null) || 
					(PatronRequest.RenewalStatus.ALLOWED == ctx.getPatronRequest().getRenewalStatus())
				)
			);
	}

  /** Return true if the supplier item is NOT renewaable */
	private static boolean shouldPreventRenewal(RequestWorkflowContext ctx) {
    if ( ctx.getSupplierRequest() != null ) {
      int hold_count = ctx.getSupplierRequest().getLocalHoldCount() != null ? ctx.getSupplierRequest().getLocalHoldCount().intValue() : 0;
      if ( hold_count > 0 )
        return true;
      if ( Boolean.FALSE.equals(ctx.getSupplierRequest().getLocalRenewable() ) )
        return true;
    }

    return false;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		log.info("PR {} detected that a hold has been placed at the owning library",ctx.getPatronRequest().getId());

    return hostLmsService.getClientFor(ctx.getPatronSystemCode())
			.flatMap(hostLmsClient -> hostLmsClient.preventRenewalOnLoan(
				PreventRenewalCommand.builder()
					.requestId(ctx.getPatronRequest().getLocalRequestId())
					.itemBarcode(ctx.getPatronRequest().getPickupItemBarcode())
					.itemId(ctx.getPatronRequest().getLocalItemId())
					.build()))
			.then( markPatronRequestNotRenewable(ctx) )
			.then( auditService.addAuditEntry(ctx.getPatronRequest(), "Hold detected at owning Library. Borrowing Library told to prevent renewals") )
      .thenReturn(ctx);
	}

	private Mono<RequestWorkflowContext> markPatronRequestNotRenewable(RequestWorkflowContext ctx) {
		// Rely upon outer framework to same 
		ctx.getPatronRequest().setRenewalStatus(PatronRequest.RenewalStatus.DISALLOWED);
		return Mono.from(patronRequestRepository.saveOrUpdate(ctx.getPatronRequest()))
			.thenReturn(ctx);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.LOANED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierHoldDetected";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of( new DCBGuardCondition("DCBPatronRequest state is LOANED and detected a hold placed on the Supplier item"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of( new DCBTransitionResult("LOANED",PatronRequest.Status.LOANED.toString()));
	}
}
