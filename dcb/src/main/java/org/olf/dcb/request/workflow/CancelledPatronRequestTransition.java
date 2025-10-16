package org.olf.dcb.request.workflow;

import static java.lang.Boolean.FALSE;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.request.fulfilment.RequestWorkflowContext.extractFromSupplierReq;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.CANCELLED;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.SupplierRequestRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Prototype
public class CancelledPatronRequestTransition implements PatronRequestStateTransition {
	private static final List<Status> POSSIBLE_SOURCE_STATUS = List.of( // Not yet loaned
		Status.REQUEST_PLACED_AT_BORROWING_AGENCY,
		Status.REQUEST_PLACED_AT_PICKUP_AGENCY,
		Status.PICKUP_TRANSIT,
		Status.RECEIVED_AT_PICKUP,
		Status.READY_FOR_PICKUP
	);
	public static final String NOT_YET_LOANED_AND_MISSING_LOCAL_HOLD = "CancelledPatronRequest : LOCAL_HOLD_MISSING";
	public static final String NOT_YET_LOANED_AND_CANCELLED_LOCAL_HOLD = "CancelledPatronRequest : LOCAL_HOLD_CANCELLED";
	public static final String PATRON_REQUEST_NOT_CANCELLED = "PATRON_REQUEST_NOT_CANCELLED";

	private final PatronRequestAuditService patronRequestAuditService;
	private final HostLmsService hostLmsService;
	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplyingAgencyService supplyingAgencyService;
	private final PickupAgencyService pickupAgencyService;

	public CancelledPatronRequestTransition(
		PatronRequestAuditService patronRequestAuditService,
		HostLmsService hostLmsService,
		SupplierRequestRepository supplierRequestRepository,
		SupplyingAgencyService supplyingAgencyService,
		PickupAgencyService pickupAgencyService) {

		this.patronRequestAuditService = patronRequestAuditService;
		this.hostLmsService = hostLmsService;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.pickupAgencyService = pickupAgencyService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {

		final var patronRequest = getValueOrNull(ctx, RequestWorkflowContext::getPatronRequest);
		final var status = getValueOrNull(patronRequest, PatronRequest::getStatus);
		final var localStatus = getValueOrNull(patronRequest, PatronRequest::getLocalRequestStatus);

		if (status == null || localStatus == null) return false;

		return switch (getReasonForCancellation(status, localStatus)) {
			case NOT_YET_LOANED_AND_MISSING_LOCAL_HOLD,
				NOT_YET_LOANED_AND_CANCELLED_LOCAL_HOLD -> true;
			default -> false;
		};
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		return auditConditionMet(ctx)
			.flatMap( cancelSupplierRequest() )
			.flatMap( cancelPickupRequest() )
			.flatMap( updatePatronRequestStatus() )
			.flatMap( verifySupplierCancellation() );
	}

	private Mono<RequestWorkflowContext> auditConditionMet(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		final var localRequestStatus = patronRequest.getLocalRequestStatus();
		final var status = patronRequest.getStatus();
		final var message = getReasonForCancellation(status, localRequestStatus);
		final var auditData = createAuditDataMap(ctx, patronRequest, localRequestStatus, status);

		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).thenReturn(ctx);
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> cancelSupplierRequest() {
		return ctx -> {

			final var localRequestStatus = extractFromSupplierReq(ctx, SupplierRequest::getLocalStatus, "LocalStatus");

			if (isRequestCancelled(localRequestStatus)) return Mono.just(ctx);

			return supplyingAgencyService.cancelHold(ctx);
		};
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> cancelPickupRequest() {
		return ctx -> {
			log.debug("cancelPickupRequest");

			final var patronRequest = ctx.getPatronRequest();
			final var activeWorkflow = patronRequest.getActiveWorkflow();

			// we may not need to cancel the pickup system request
			// we assume there is a pickup request if the active workflow is pickup anywhere
			if (!PICKUP_ANYWHERE_WORKFLOW.equals(activeWorkflow)) {

				log.debug("cancelPickupRequest not needed for active workflow");

				return Mono.just(ctx);
			}

			final var pickupRequestStatus = patronRequest.getPickupRequestStatus();

			// tracking may have already picked up the request was cancelled at the pickup system
			if (isRequestCancelled(pickupRequestStatus)) {
				log.warn("cancelPickupRequest pickup request already cancelled");

				return Mono.just(ctx);
			}

			return pickupAgencyService.cancelHoldIfPresent(ctx)
				.thenReturn(ctx);
		};
	}

	private static HashMap<String, Object> createAuditDataMap(RequestWorkflowContext ctx, PatronRequest patronRequest,
		String localRequestStatus, Status status) {
		var auditData = new HashMap<String, Object>();

		// borrower data
		auditData.put("dcb-patron-request-status-on-entry", status);
		auditData.put("local-patron-request-status-on-entry", localRequestStatus);
		auditData.put("virtual-item-status-on-entry", getValueOrNull(patronRequest, PatronRequest::getLocalItemStatus));

		// lender data
		final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
		auditData.put("dcb-supplier-request-status-on-entry", getValueOrNull(supplierRequest, SupplierRequest::getStatusCode));
		auditData.put("local-supplier-request-status-on-entry", getValueOrNull(supplierRequest, SupplierRequest::getLocalStatus));
		auditData.put("local-supplier-item-status-on-entry", getValueOrNull(supplierRequest, SupplierRequest::getLocalItemStatus));

		return auditData;
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> verifySupplierCancellation() {
		return ctx -> {
			final var supplierRequest = getValueOrNull(ctx, RequestWorkflowContext::getSupplierRequest);
			final var result = getName() + " : verification result";

			return fetchLocal(supplierRequest)
				.flatMap( verify(ctx, result) )
				.onErrorResume( auditVerificationError(ctx, result) );
		};
	}

	private Mono<HostLmsRequest> fetchLocal(SupplierRequest supplierRequest) {
		final var localRequestId = supplierRequest.getLocalId();
		final var supplierPatronId = getValueOrNull(supplierRequest, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(localRequestId).localPatronId(supplierPatronId).build();

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.getRequest(hostlmsRequest));
	}

	private Function<HostLmsRequest, Mono<RequestWorkflowContext>> verify(
		RequestWorkflowContext ctx, String resultString) {

		return request -> {

			final var isCancelled = isRequestCancelled(request.getStatus());
			var auditData = new HashMap<String, Object>();

			auditData.put("is-local-supplier-request-cancelled", isCancelled);
			auditData.put("local-supplier-request-mapped-status",
				getValue(request, HostLmsRequest::getStatus, "No local mapped status available"));
			auditData.put("local-supplier-request-raw-status",
				getValue(request, HostLmsRequest::getRawStatus, "No local raw status available"));
			auditData.put("local-supplier-request", request);

			return patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), resultString, auditData)
				.then( updateSupplierRequest(isCancelled, ctx) );
		};
	}

	private Mono<RequestWorkflowContext> updateSupplierRequest(Boolean cancelled, RequestWorkflowContext context) {
		if (!cancelled) return Mono.just(context);

		context.getSupplierRequest().setStatusCode(CANCELLED);
		return Mono.from(supplierRequestRepository.update(context.getSupplierRequest())).thenReturn(context);
	}

	private Function<Throwable, Mono<RequestWorkflowContext>> auditVerificationError(
		RequestWorkflowContext ctx, String result) {

		return error -> {

			var auditData = new HashMap<String, Object>();
			auditData.put("is-local-supplier-request-cancelled", FALSE);
			auditData.put("ErrorMessage", getValue(error, Throwable::getMessage, "No error message available"));
			auditData.put("Full error", error.toString());

			return patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), result, auditData).thenReturn(ctx);
		};
	}

	private static Boolean isRequestCancelled(String status) {
		return HOLD_CANCELLED.equals(status) || HOLD_MISSING.equals(status);
	}

	private Function<RequestWorkflowContext, Mono<RequestWorkflowContext>> updatePatronRequestStatus() {
		return ctx -> Mono.defer(() -> Mono.just(ctx.getPatronRequest().setStatus(Status.CANCELLED)))
			.thenReturn(ctx);
	}

	private static String getReasonForCancellation(Status status, String localRequestStatus) {
		boolean isNotYetLoaned = isNotYetLoaned(status);
		boolean isLocalBorrowingRequestMissing = isLocalBorrowingRequestMissing(localRequestStatus);
		boolean isLocalBorrowingRequestCancelled = isLocalBorrowingRequestCancelled(localRequestStatus);

		if (isNotYetLoaned && isLocalBorrowingRequestMissing) {
			return NOT_YET_LOANED_AND_MISSING_LOCAL_HOLD;
		}

		else if (isNotYetLoaned && isLocalBorrowingRequestCancelled) {
			return NOT_YET_LOANED_AND_CANCELLED_LOCAL_HOLD;
		}

		return PATRON_REQUEST_NOT_CANCELLED;
	}

	private static boolean isNotYetLoaned(Status requestStatus) {
		return POSSIBLE_SOURCE_STATUS.contains(requestStatus);
	}

	private static boolean isLocalBorrowingRequestCancelled(String localRequestStatus) {
		return HOLD_CANCELLED.equals(localRequestStatus);
	}

	private static boolean isLocalBorrowingRequestMissing(String localRequestStatus) {
		return HOLD_MISSING.equals(localRequestStatus);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return POSSIBLE_SOURCE_STATUS;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.CANCELLED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "CancelledPatronRequestTransition";
	}
}
