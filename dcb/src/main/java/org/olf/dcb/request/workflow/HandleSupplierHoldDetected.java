package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import org.olf.dcb.storage.PatronRequestRepository;

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
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final PatronRequestAuditService auditService;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.LOANED);
	
	public HandleSupplierHoldDetected(PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService auditService, HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.auditService = auditService;
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

		/** Return true if the supplier item is NOT renewable - but allow the initial loan.
		 * We detect the initial loan when the local renewal count and the renewal count are zero: this indicates that DCB is not aware of any external renewal, and has not triggered one.*/
	private static boolean shouldPreventRenewal(RequestWorkflowContext ctx) {
		log.debug("The context is {}", ctx);
		if ( ctx.getSupplierRequest() != null ) {
			int hold_count = ctx.getSupplierRequest().getLocalHoldCount() != null ? ctx.getSupplierRequest().getLocalHoldCount().intValue() : 0;
			int renewal_count = ctx.getPatronRequest().getRenewalCount() != null ? ctx.getPatronRequest().getRenewalCount() : 0;
			int local_renewal_count = ctx.getPatronRequest().getLocalRenewalCount() != null ? ctx.getPatronRequest().getLocalRenewalCount() : 0;
			boolean renewable = ctx.getPatronRequest().getRenewalStatus() != null && ctx.getPatronRequest().getRenewalStatus().equals(PatronRequest.RenewalStatus.ALLOWED);
			boolean supplierRenewable = ctx.getSupplierRequest().getLocalRenewable() != null && ctx.getSupplierRequest().getLocalRenewable();

			// In the unlikely event renewal status is null, we should prevent renewal
			if (ctx.getPatronRequest().getRenewalStatus() == null)
			{
				return true;
			}
			// A guard to stop the initial loan from being prevented - see DCB-2006
			if (renewal_count == 0 && local_renewal_count == 0)
				return false;
			// I have deliberately kept this extremely basic for readability purposes - CH
			// If the supplier request OR the main request are renewable, we must not prevent renewal.
			// This is what was causing the regression failure for 8.46.1
			if (renewable || supplierRenewable)
			{
				return false;
			}
			// Now, we will only prevent renewal on requests with a hold count of greater than zero that have not been flagged as renewable
			if ( hold_count > 0 )
        return true;

			// If renewable is false, and also the local renewal count is greater than zero (i.e. not the initial loan)
			return Boolean.FALSE.equals(ctx.getSupplierRequest().getLocalRenewable());
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
