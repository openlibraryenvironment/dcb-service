package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * A person at the supplying library, or consortium staff, confirms the item is back on its shelf.
 * That stands in for the return signal the supplier never sent, and the automatic transitions that
 * wait for it release the request: see docs/cancellation-handling.md, "Confirming an item is back".
 */
@Slf4j
@Singleton
public class ConfirmItemReturnedService {
	// DCB already believes the item is on its way home in these, and waits only for the supplier to say so
	private static final Set<Status> AWAITING_RETURN = EnumSet.of(
		Status.AWAITING_RETURN_TO_SUPPLIER, Status.RETURN_TRANSIT);

	private static final Set<Status> RELEASED = EnumSet.of(
		Status.CANCELLED, Status.COMPLETED, Status.FINALISED);

	static final String CONFIRMED_RAW_STATUS = "CONFIRMED_RETURNED_BY_STAFF";
	static final String CONFIRMED = "Item confirmed back at the supplying library";

	private final PatronRequestService patronRequestService;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	private final PatronRequestWorkflowService workflowService;

	public ConfirmItemReturnedService(PatronRequestService patronRequestService,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService patronRequestAuditService,
		PatronRequestWorkflowService workflowService) {

		this.patronRequestService = patronRequestService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
		this.workflowService = workflowService;
	}

	public Mono<UUID> confirm(UUID patronRequestId, CallerScope caller) {
		// A library caller is told 403 for a request that does not exist too, so it cannot probe ids
		return patronRequestService.findById(patronRequestId)
			.switchIfEmpty(Mono.error(() -> caller.requiresNarrowing()
				? forbidden(patronRequestId)
				: problem(org.zalando.problem.Status.NOT_FOUND, "No such patron request",
					"No patron request has this id.", patronRequestId, null)))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.map(ctx -> ensureConfirmableBy(ctx, caller))
			.map(ConfirmItemReturnedService::ensureAwaitingReturn)
			.flatMap(ctx -> recordReturn(ctx, caller))
			.flatMap(workflowService::progressUsing)
			.then(Mono.defer(() -> patronRequestService.findById(patronRequestId)))
			.map(ConfirmItemReturnedService::ensureReleased)
			.map(PatronRequest::getId);
	}

	// Only the library holding the active supplier request knows its shelf; an earlier supplier does not
	static RequestWorkflowContext ensureConfirmableBy(RequestWorkflowContext ctx, CallerScope caller) {
		if (!caller.requiresNarrowing()) {
			return ctx;
		}

		final var supplierAgency = getValueOrNull(ctx,
			RequestWorkflowContext::getSupplierRequest, SupplierRequest::getLocalAgency);

		if (caller.isIncoherent() || supplierAgency == null || !caller.agencyCodes().contains(supplierAgency)) {
			log.warn("Refusing to confirm the return of patron request {}: caller {} is not its supplier {}",
				ctx.getPatronRequest().getId(), caller.agencyCodes(), supplierAgency);

			throw forbidden(ctx.getPatronRequest().getId());
		}

		return ctx;
	}

	static RequestWorkflowContext ensureAwaitingReturn(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		final var status = patronRequest.getStatus();

		if (!AWAITING_RETURN.contains(status)) {
			throw problem(org.zalando.problem.Status.CONFLICT, "This request is not waiting for its item to return",
				("Only a request whose item is on its way back to the supplying library can be confirmed "
					+ "returned. This request is %s.").formatted(status), patronRequest.getId(), status);
		}

		if (ctx.getSupplierRequest() == null) {
			throw problem(org.zalando.problem.Status.CONFLICT, "This request has no supplier to return to",
				"No active supplier request is recorded for this request.", patronRequest.getId(), status);
		}

		return ctx;
	}

	private Mono<RequestWorkflowContext> recordReturn(RequestWorkflowContext ctx, CallerScope caller) {
		final var supplierRequest = ctx.getSupplierRequest();
		final var previousItemStatus = supplierRequest.getLocalItemStatus();

		supplierRequest
			.setLocalItemStatus(ITEM_AVAILABLE)
			.setRawLocalItemStatus(CONFIRMED_RAW_STATUS)
			.setLocalItemLastCheckTimestamp(Instant.now());

		final var auditData = new HashMap<String, Object>();
		// The library, never the person: the audit is read by every library party to the request
		auditData.put("confirmedBy", caller.requiresNarrowing() ? supplierRequest.getLocalAgency() : "consortium");
		auditData.put("supplierHostLmsCode", supplierRequest.getHostLmsCode());
		auditData.put("supplierItemStatusBefore", previousItemStatus);
		auditData.put("patronRequestStatusBefore", String.valueOf(ctx.getPatronRequest().getStatus()));

		return Mono.from(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.then(patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), CONFIRMED, auditData))
			.thenReturn(ctx);
	}

	// Transition errors are audited and swallowed inside the workflow service, so the status is the only
	// evidence of whether the release happened
	static PatronRequest ensureReleased(PatronRequest patronRequest) {
		if (RELEASED.contains(patronRequest.getStatus())) {
			return patronRequest;
		}

		throw problem(org.zalando.problem.Status.INTERNAL_SERVER_ERROR, "The request was not released",
			"The request is still %s after the return was confirmed; see its audit log."
				.formatted(patronRequest.getStatus()), patronRequest.getId(), patronRequest.getStatus());
	}

	private static ThrowableProblem forbidden(UUID patronRequestId) {
		return problem(org.zalando.problem.Status.FORBIDDEN, "Access denied",
			"Only the library supplying this request, or consortium staff, can confirm its item is back.",
			patronRequestId, null);
	}

	private static ThrowableProblem problem(org.zalando.problem.Status status, String title, String detail,
		UUID patronRequestId, Status patronRequestStatus) {

		final var builder = Problem.builder()
			.withTitle(title)
			.withStatus(status)
			.withDetail(detail)
			.with("patronRequestId", String.valueOf(patronRequestId));

		// "status" is a reserved Problem property and throws if used here
		return patronRequestStatus == null
			? builder.build()
			: builder.with("patronRequestStatus", String.valueOf(patronRequestStatus)).build();
	}
}
