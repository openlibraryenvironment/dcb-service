package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 * HandleCancelledRequestItemOut.
 * The patron's hold has gone while the item is physically "out" of the supplying library - the DCB
 * status is PICKUP_TRANSIT, RECEIVED_AT_PICKUP or READY_FOR_PICKUP. Auto-finalising here (the default
 * cancellation behaviour) would delete the borrowing library's virtual records while the item is out,
 * orphaning the physical item so the supplier eventually writes it off as lost. So we park instead.
 *
 * <p><b>It parks unconditionally.</b> The request is never cancelled from here, whatever the supplier is.
 * Cancelling finalises, and finalisation deletes the borrowing library's virtual item and bib - doing
 * that while the physical item is still out is exactly the orphaned-item bug this transition exists to
 * prevent. A supplier that cannot report the item's return is flagged for manual release, not finalised:
 * its tracking limitations are not the borrowing library's to pay for.
 *
 * <p>We DELETE the supplier hold via cleanUp - each client implements the correct terminal operation for
 * its ILS. The supplier hold is normally consumed by the supplier-side checkout, but that only happens
 * once the patron actually loans the item; here nothing consumed it. Left in place it re-captures the
 * item when it is checked back in at the supplier (Polaris reports "transfer for hold" and routes it
 * straight back out), so it must go. Whether that actually succeeded is verified, not assumed - parking
 * a request whose hold is still live would wait for an AVAILABLE that can never arrive.
 *
 * <p>No borrower LMS call is needed: the borrower/pickup hold is already gone, which is this
 * transition's trigger. This is the PATRON cancellation path; supplier cancellation / re-resolution is
 * a separate concern (HandleSupplierRequestCancelled) that this transition never interacts with.
 *
 * <p><b>The item must not be with the patron.</b> Sierra and Polaris consume the local hold on checkout,
 * so "hold gone" on its own also describes a completely normal collection. Tracking polls the request
 * before the item and progresses the workflow once at the end of the cycle, so the engine sees both the
 * missing hold and the LOANED item in the same context - and breaks the tie by reverse-alphabetical
 * transition name, where this class outranks HandleBorrowerItemLoaned. Without the item-status gate
 * below, every successful pickup would be parked as a cancellation.
 *
 * <p><b>A genuinely missed loan is indistinguishable from a cancellation</b> and is handled here too
 * (this transition subsumes the former HandleBorrowerSkippedLoanTransit). If DCB never observed the
 * loan, there is no signal separating "the patron cancelled" from "we missed the checkout", and the
 * physically correct response is the same either way: terminate the hold so the item is not re-captured,
 * and keep the records until it is confirmed home. Both are recorded as Outcome.CANCELLED.
 */
@Slf4j
@Singleton
@Named("CancelledRequestItemOut")
public class HandleCancelledRequestItemOut implements PatronRequestStateTransition {
	private static final List<Status> possibleSourceStatus = List.of(
		Status.PICKUP_TRANSIT,
		Status.RECEIVED_AT_PICKUP,
		Status.READY_FOR_PICKUP
	);
	private static final List<String> cancelledHoldStatus = List.of(HOLD_MISSING, HOLD_CANCELLED);

	static final String ITEM_OUT_HELD_AWAITING_RETURN =
		"CancelledRequestItemOut : patron hold gone while item is out, awaiting its return to the supplier";
	static final String RETURN_NOT_REPORTABLE_NEEDS_ATTENTION =
		"CancelledRequestItemOut : supplier cannot report this item's return - the request will stay parked "
			+ "until someone confirms the item is home and cleans it up with force=true";
	static final String SUPPLIER_HOLD_VERIFICATION =
		"CancelledRequestItemOut : supplier hold termination verification";
	static final String DECLARATIVE_SUPPLIER_SKIPPED =
		"CancelledRequestItemOut : declarative supplier hold termination is not implemented; skipping imperative cleanup";

	private final PatronRequestAuditService patronRequestAuditService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final HostLmsService hostLmsService;
	private final LifecycleCapabilityResolver capabilityResolver;
	private final AlarmsService alarmsService;

	public HandleCancelledRequestItemOut(PatronRequestAuditService patronRequestAuditService,
		SupplyingAgencyService supplyingAgencyService,
		HostLmsService hostLmsService,
		LifecycleCapabilityResolver capabilityResolver,
		AlarmsService alarmsService) {

		this.patronRequestAuditService = patronRequestAuditService;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.capabilityResolver = capabilityResolver;
		this.alarmsService = alarmsService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);

		if (status == null || !possibleSourceStatus.contains(status)) return false;
		if (getValueOrNull(patronRequest, PatronRequest::getActiveWorkflow) == null) return false;

		if (!isPatronHoldGone(patronRequest)) return false;

		// The hold is also consumed when the patron simply collects the item. If the item is with the
		// patron this is a loan, not a cancellation - leave it to HandleBorrowerItemLoaned.
		return !isItemWithPatron(patronRequest);
	}

	/**
	 * The patron's own hold is the one at their home (borrowing) library - that is what they see and what
	 * they cancel, in Pickup Anywhere as much as anywhere else. The pickup hold is one DCB placed against
	 * a virtual patron so the item can sit on the pickup shelf, and it disappears for its own reasons
	 * (notably being consumed at collection, which the item-status gate catches).
	 * <p>
	 * For PUA we therefore watch BOTH: the borrower hold because that is the patron's, and the pickup
	 * hold because losing it also means the item is out with nothing holding it. Watching only the pickup
	 * hold silently drops every PUA patron cancellation - nothing else claims those states.
	 */
	private static boolean isPatronHoldGone(PatronRequest patronRequest) {
		if (cancelledHoldStatus.contains(
			getValueOrNull(patronRequest, PatronRequest::getLocalRequestStatus))) {

			return true;
		}

		return patronRequest.isUsingPickupAnywhereWorkflow()
			&& cancelledHoldStatus.contains(
				getValueOrNull(patronRequest, PatronRequest::getPickupRequestStatus));
	}

	/**
	 * Either side reporting the item loaned means the patron has it. Mirrors HandleBorrowerItemLoaned,
	 * which triggers on either, so the two stay in step.
	 */
	private static boolean isItemWithPatron(PatronRequest patronRequest) {
		return ITEM_LOANED.equals(getValueOrNull(patronRequest, PatronRequest::getLocalItemStatus))
			|| ITEM_LOANED.equals(getValueOrNull(patronRequest, PatronRequest::getPickupItemStatus));
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		return terminateSupplierHold(ctx)
			.flatMap(this::verifySupplierHoldTerminated)
			.flatMap(this::park)
			.flatMap(this::flagIfReturnCannotBeReported);
	}

	private Mono<RequestWorkflowContext> park(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();

		return patronRequestAuditService
			.addAuditEntry(patronRequest, ITEM_OUT_HELD_AWAITING_RETURN, buildAuditData(patronRequest))
			.map(audit -> {
				patronRequest.setStatus(Status.AWAITING_RETURN_TO_SUPPLIER);
				return ctx;
			});
	}

	/**
	 * Some suppliers cannot report the item's return once their hold is terminated - a FOLIO supplier
	 * today, because cancelling the mod-dcb transaction is also what destroys the only channel DCB can
	 * observe the item through. Nothing will release such a request automatically.
	 * <p>
	 * That is a reason to raise a flag, and never a reason to cancel. Cancelling finalises, finalisation
	 * deletes the borrowing library's virtual item, and doing that while the real item is still out is the
	 * orphaned-item bug this transition exists to prevent - the supplier's tracking limitations are not
	 * the borrower's problem to pay for. A parked request a human can see and release (POST
	 * .../transition/cleanup?force=true, once they have confirmed the item is home) beats a deleted record
	 * they cannot recover.
	 */
	private Mono<RequestWorkflowContext> flagIfReturnCannotBeReported(RequestWorkflowContext ctx) {
		final var hostLmsCode = getValueOrNull(ctx,
			RequestWorkflowContext::getSupplierRequest, SupplierRequest::getHostLmsCode);

		if (hostLmsCode == null) return Mono.just(ctx);

		return hostLmsService.getClientFor(hostLmsCode)
			.map(HostLmsClient::canReportItemReturnedAfterHoldTerminated)
			// Assume it can report if we cannot tell; a spurious flag is noise, and the request is parked
			// safely either way.
			.onErrorReturn(Boolean.TRUE)
			.defaultIfEmpty(Boolean.TRUE)
			.flatMap(canReport -> Boolean.TRUE.equals(canReport)
				? Mono.just(ctx)
				: raiseNeedsManualReleaseAlarm(ctx, hostLmsCode));
	}

	private Mono<RequestWorkflowContext> raiseNeedsManualReleaseAlarm(
		RequestWorkflowContext ctx, String hostLmsCode) {

		final var auditData = new HashMap<String, Object>();
		auditData.put("supplierHostLmsCode", hostLmsCode);
		auditData.put("reason", "Supplier cannot report the item's return once its hold has been terminated");
		auditData.put("resolution", "Confirm the item is back at the supplier, then clean up with force=true");

		// Alarm code is bounded by the number of host LMS, never by request - no high-cardinality keys.
		final var alarmCode = "SYSTEM.REQ-WORKFLOW.CANCEL-WHILE-OUT.NO-RETURN-SIGNAL." + hostLmsCode;

		return alarmsService.raise(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.build())
			.onErrorResume(error -> {
				log.error("Could not raise no-return-signal alarm for {}", hostLmsCode, error);
				return Mono.empty();
			})
			.then(patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(),
				RETURN_NOT_REPORTABLE_NEEDS_ATTENTION, auditData))
			.thenReturn(ctx);
	}

	/**
	 * Terminating a declarative supplier's obligation is a protocol message, not a REST hold delete.
	 * Discriminate on the resolved placement strategy rather than the persisted protocol string -
	 * Foundation is an imperative NCIP adapter, so a non-blank protocol does not mean declarative, and
	 * misreading it would silently skip a cleanup that is genuinely needed. An unknown host falls back to
	 * instance config, which defaults to IMPERATIVE, so we still attempt cleanup.
	 */
	private Mono<RequestWorkflowContext> terminateSupplierHold(RequestWorkflowContext ctx) {
		final var lenderSystem = getValueOrNull(ctx, RequestWorkflowContext::getLenderSystem);

		if (capabilityResolver.placementStrategy(lenderSystem, LifecycleRole.SUPPLIER)
			== StrategyType.DECLARATIVE) {

			return patronRequestAuditService
				.addAuditEntry(ctx.getPatronRequest(), DECLARATIVE_SUPPLIER_SKIPPED, buildSupplierAuditData(ctx))
				.thenReturn(ctx);
		}

		return supplyingAgencyService.cleanUp(ctx);
	}

	/**
	 * The whole point of parking is that the item can still be observed coming home. That only holds if
	 * the supplier hold really did go - a surviving hold re-captures the item on check-in, so it never
	 * reports AVAILABLE and the request waits forever. cleanUp swallows its own failures, so ask the
	 * supplier directly rather than trusting it.
	 */
	private Mono<RequestWorkflowContext> verifySupplierHoldTerminated(RequestWorkflowContext ctx) {
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		final var hostLmsCode = getValueOrNull(supplierRequest, SupplierRequest::getHostLmsCode);
		final var localRequestId = getValueOrNull(supplierRequest, SupplierRequest::getLocalId);

		if (hostLmsCode == null || localRequestId == null) return Mono.just(ctx);

		final var supplierPatronId = getValueOrNull(supplierRequest,
			SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);

		final var hostLmsRequest = HostLmsRequest.builder()
			.localId(localRequestId)
			.localPatronId(supplierPatronId)
			.build();

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getRequest(hostLmsRequest))
			.map(hold -> auditDataFor(localRequestId, getValueOrNull(hold, HostLmsRequest::getStatus)))
			// No hold to report means it is gone, which is what we wanted.
			.defaultIfEmpty(auditDataFor(localRequestId, HOLD_MISSING))
			.onErrorResume(error -> {
				final var auditData = auditDataFor(localRequestId, "Unknown");
				auditThrowable(auditData, "Throwable", error);
				return Mono.just(auditData);
			})
			.flatMap(auditData -> patronRequestAuditService
				.addAuditEntry(ctx.getPatronRequest(), SUPPLIER_HOLD_VERIFICATION, auditData))
			.thenReturn(ctx);
	}

	private static HashMap<String, Object> auditDataFor(String localRequestId, String holdStatus) {
		final var auditData = new HashMap<String, Object>();
		auditData.put("localRequestId", localRequestId);
		auditData.put("supplier-hold-status-after-termination", getValue(holdStatus, "Unknown"));
		// A hold that survives leaves the item re-capturable, so the request will never release itself.
		auditData.put("supplier-hold-terminated",
			HOLD_MISSING.equals(holdStatus) || HOLD_CANCELLED.equals(holdStatus));
		return auditData;
	}

	private static HashMap<String, Object> buildSupplierAuditData(RequestWorkflowContext ctx) {
		final var auditData = new HashMap<String, Object>();
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);

		auditData.put("supplierHostLmsCode", getValue(supplierRequest, SupplierRequest::getHostLmsCode, "Unknown"));
		auditData.put("supplierRequestId", getValue(supplierRequest, SupplierRequest::getLocalId, "Unknown"));
		return auditData;
	}

	private static HashMap<String, Object> buildAuditData(PatronRequest patronRequest) {
		var auditData = new HashMap<String, Object>();
		auditData.put("dcb-patron-request-status-on-entry", patronRequest.getStatus());
		auditData.put("local-patron-request-status-on-entry", patronRequest.getLocalRequestStatus());
		auditData.put("pickup-patron-request-status-on-entry", patronRequest.getPickupRequestStatus());
		auditData.put("virtual-item-status-on-entry", patronRequest.getLocalItemStatus());
		auditData.put("pickup-item-status-on-entry", patronRequest.getPickupItemStatus());
		return auditData;
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.AWAITING_RETURN_TO_SUPPLIER);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleCancelledRequestItemOut";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition(
			"DCBPatronRequest state is PICKUP_TRANSIT, RECEIVED_AT_PICKUP or READY_FOR_PICKUP, the borrower hold "
				+ "(or for Pickup Anywhere either the borrower or the pickup hold) is MISSING or CANCELLED, and "
				+ "the item is not loaned to the patron"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(
			new DCBTransitionResult("HELD", Status.AWAITING_RETURN_TO_SUPPLIER.toString()));
	}
}
