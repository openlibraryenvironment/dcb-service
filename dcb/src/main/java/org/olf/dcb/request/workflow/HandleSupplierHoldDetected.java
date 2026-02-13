package org.olf.dcb.request.workflow;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsItem;
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
import org.olf.dcb.storage.SupplierRequestRepository;
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
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestAuditService auditService;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(Status.LOANED);
	
	public HandleSupplierHoldDetected(PatronRequestRepository patronRequestRepository, SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService auditService, HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.auditService = auditService;
		this.hostLmsService = hostLmsService;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		// Applicable if LOANED, if supplier request is not null, and a hold count exists at the supplier
		// And of course only if the renewal status is ALLOWED (i.e. we didn't already prevent renewals, and renewals are supported)
		// A potential extension might be to check the item status here too: if it will never be renewable, we could save some pain and auto-prevent renewals.

		return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) ) &&
			( ctx.getSupplierRequest() != null ) &&
				shouldPreventRenewal(ctx) && 	(
					(ctx.getPatronRequest().getRenewalStatus() == null) ||
					(PatronRequest.RenewalStatus.ALLOWED == ctx.getPatronRequest().getRenewalStatus()));
	}

	/** Return true if there is still a hold on the supplier after checkout.
	 * But we need some guards just in case we've not got the correct hold count.
	 * And we need to make sure we allow the initial loan, as sometimes there is a delay*/

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("PR {} detected that a hold has been placed at the owning library. verifying...", ctx.getPatronRequest().getId());

		// At this point, we think we have detected a hold. But we need to check that
		// As sometimes DCB will not update the local hold count in time
		// So we will end up always preventing renewals if we do not check the current value.
		final var supplierRequest = ctx.getSupplierRequest();
		final var supplierSystemCode = supplierRequest.getHostLmsCode();

		final var supplierItemId = HostLmsItem.builder()
			.localId(supplierRequest.getLocalItemId())
			.localRequestId(supplierRequest.getLocalId())
			.build();

		// Quick check of the actual item. Does it really have a hold?
		return hostLmsService.getClientFor(supplierSystemCode)
			.flatMap(client -> Mono.from(client.getItem(supplierItemId)))
			.flatMap(freshItem -> {
				log.info("Item coming back {}", freshItem);
				int freshHoldCount = freshItem.getHoldCount() != null ? freshItem.getHoldCount() : 0;
				// We set the new value, and, just to be sure, we persist it also.
				// We should probably NOT do this if it's the same as the old hold count
				// We should also probably update the whole item on the supplier request?
				supplierRequest.setLocalHoldCount(freshHoldCount);
				return Mono.from(supplierRequestRepository.saveOrUpdate(supplierRequest))
					.doOnSuccess(sr -> log.debug("Updated local hold count cache for PR {} to {}", ctx.getPatronRequest().getId(), freshHoldCount))
					.thenReturn(freshHoldCount);
			})
			.flatMap(freshHoldCount -> {
				// Now we know that we have got the up-to-date hold value
				if (freshHoldCount > 0) {
					log.debug("Holds confirmed ({}). Preventing renewals at borrowing library.", freshHoldCount);
					return executePreventRenewal(ctx);
				} else {
					log.info("False positive detected (Count is 0). DB updated. Skipping renewal prevention.");
					return auditService.addAuditEntry(ctx.getPatronRequest(),
							"Renewal not prevented: hold count checked in the local system and verified as 0.") //
						.thenReturn(ctx);				}
			})
			.onErrorResume(error -> {
				log.error("Error verifying supplier hold count for PR {}. Defaulting to proceeding with caution.", ctx.getPatronRequest().getId(), error);
				return auditService.addAuditEntry(ctx.getPatronRequest(),
						"Error verifying supplier hold count (" + error.getMessage() + "). Defaulting to preventing renewals.") //
					.then(executePreventRenewal(ctx));
			});
	}

	private Mono<RequestWorkflowContext> executePreventRenewal(RequestWorkflowContext ctx) {
		return hostLmsService.getClientFor(ctx.getPatronSystemCode())
			.flatMap(hostLmsClient -> hostLmsClient.preventRenewalOnLoan(
				PreventRenewalCommand.builder()
					.requestId(ctx.getPatronRequest().getLocalRequestId())
					.itemBarcode(ctx.getPatronRequest().getPickupItemBarcode())
					.itemId(ctx.getPatronRequest().getLocalItemId())
					.build()))
			.then(markPatronRequestNotRenewable(ctx))
			.flatMap(updatedCtx -> auditService.addAuditEntry(updatedCtx.getPatronRequest(),
					"Hold confirmed at owning Library. Borrowing Library told to prevent renewals")
				.thenReturn(updatedCtx)
			)
			.onErrorResume(error -> {
			log.warn("Failed to prevent renewal for PR {}: {}", ctx.getPatronRequest().getId(), error.getMessage());
			return auditService.addAuditEntry(ctx.getPatronRequest(),
					"Failed to prevent renewal at borrowing library: " + error.getMessage()) //
				.then(markPatronRequestRenewalUnsupported(ctx));
		});
	}

	/** Return true if there is still a hold on the supplier after checkout.
	 * But we need some guards just in case we've not got the correct hold count.
	 * And we need to make sure we allow the initial loan, as sometimes there is a delay*/
	private static boolean shouldPreventRenewal(RequestWorkflowContext ctx) {
		if (ctx.getSupplierRequest() != null) {
			int hold_count = ctx.getSupplierRequest().getLocalHoldCount() != null ? ctx.getSupplierRequest().getLocalHoldCount() : 0;
			// When we reach LOANED, the following questions need to be asked
			// Does the SUPPLIER item have a hold?
			// If no, we should NOT prevent renewal
			// If we think it does have a hold, we must be sure that this is a current hold, and we're not lagging detecting our own hold
			if (hold_count == 0) {
				log.debug("No holds! Renewal is allowed");
				return false;
			} else {
				log.debug("Hold up! Renewal not allowed ..");
				return true;
			}
		} else {
			// Fail-safe: do not prevent renewals unless we are sure there are still holds
			// See the 8.46.1 regression failure where everything broke - CH
			return false;
		}
	};

	private Mono<RequestWorkflowContext> markPatronRequestNotRenewable(RequestWorkflowContext ctx) {
		// Rely upon outer framework to same 
		ctx.getPatronRequest().setRenewalStatus(PatronRequest.RenewalStatus.DISALLOWED);
		return Mono.from(patronRequestRepository.saveOrUpdate(ctx.getPatronRequest()))
			.thenReturn(ctx);
	}

	// This is used if renewal prevention fails.
	// It should ensure we don't get an infinite loop, and the request can continue
	// But it makes it clear that something is not right.
	private Mono<RequestWorkflowContext> markPatronRequestRenewalUnsupported(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().setRenewalStatus(PatronRequest.RenewalStatus.UNSUPPORTED);
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
