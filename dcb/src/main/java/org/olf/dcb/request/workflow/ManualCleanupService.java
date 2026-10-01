package org.olf.dcb.request.workflow;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Staff-initiated cleanup. Cleanup deletes the borrowing library's virtual item and bib, which orphans
 * the physical item if it is not back at the supplier yet (DCB-2193) - so item-out states are refused
 * unless the caller explicitly overrides.
 */
@Slf4j
@Singleton
public class ManualCleanupService {
	private static final Set<Status> ITEM_OUT_STATUSES = EnumSet.of(
		Status.PICKUP_TRANSIT, Status.RECEIVED_AT_PICKUP, Status.READY_FOR_PICKUP,
		Status.LOANED, Status.RETURN_TRANSIT, Status.AWAITING_RETURN_TO_SUPPLIER);

	private static final Set<Status> CLEANED_UP = EnumSet.of(Status.COMPLETED, Status.FINALISED);

	private static final URI ERR_CLEANUP = URI.create(
		"https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/cleanup-while-item-is-out");

	private final PatronRequestService patronRequestService;
	private final PatronRequestWorkflowService workflowService;
	private final CleanupPatronRequestTransition cleanupTransition;

	public ManualCleanupService(PatronRequestService patronRequestService,
		PatronRequestWorkflowService workflowService,
		CleanupPatronRequestTransition cleanupTransition) {

		this.patronRequestService = patronRequestService;
		this.workflowService = workflowService;
		this.cleanupTransition = cleanupTransition;
	}

	public Mono<UUID> cleanup(UUID patronRequestId, boolean override) {
		return patronRequestService.findById(patronRequestId)
			.switchIfEmpty(Mono.error(() -> problem(org.zalando.problem.Status.NOT_FOUND,
				"No such patron request", "No patron request has this id.", patronRequestId)))
			.map(patronRequest -> ensureCleanupPermitted(patronRequest, override))
			.map(this::ensureCleanupApplies)
			.flatMap(patronRequest -> workflowService.progressUsing(patronRequest, cleanupTransition))
			.then(Mono.defer(() -> patronRequestService.findById(patronRequestId)))
			.map(ManualCleanupService::ensureCleanedUp)
			.map(PatronRequest::getId);
	}

	public static PatronRequest ensureCleanupPermitted(PatronRequest patronRequest, boolean override) {
		final var status = patronRequest.getStatus();

		// ERROR is never polled again, so its status is frozen at the moment it failed; previousStatus
		// is the only record of whether the item was out.
		if (Status.ERROR.equals(status) && ITEM_OUT_STATUSES.contains(patronRequest.getPreviousStatus())) {
			return refuseUnlessOverridden(patronRequest, override,
				("This request errored while the item was out (last known state %s), and errored requests are "
					+ "not polled again, so DCB cannot confirm where the item is now. Cleaning up would delete "
					+ "the borrowing library's virtual records. Confirm the item is back at the supplying "
					+ "library, then repeat with force=true.").formatted(patronRequest.getPreviousStatus()),
				patronRequest.getPreviousStatus());
		}

		if (ITEM_OUT_STATUSES.contains(status)) {
			return refuseUnlessOverridden(patronRequest, override,
				("The item for this request is not back at the supplying library (status %s). Cleaning up now "
					+ "would delete the borrowing library's virtual records and orphan the physical item. Wait "
					+ "for the item to be returned, or repeat with force=true if you are certain the item is "
					+ "accounted for.").formatted(status),
				status);
		}

		if (Status.CANCELLED.equals(status)) {
			throw Problem.builder()
				.withType(ERR_CLEANUP)
				.withTitle("Cannot transition cancelled requests")
				.withStatus(org.zalando.problem.Status.CONFLICT)
				.withDetail("This request is already cancelled and will finalise on its own.")
				.with("patronRequestId", String.valueOf(patronRequest.getId()))
				.build();
		}

		return patronRequest;
	}

	private PatronRequest ensureCleanupApplies(PatronRequest patronRequest) {
		if (CLEANED_UP.contains(patronRequest.getStatus())
			|| cleanupTransition.getPossibleSourceStatus().contains(patronRequest.getStatus())) {

			return patronRequest;
		}

		throw Problem.builder()
			.withType(ERR_CLEANUP)
			.withTitle("Cleanup is not available from this status")
			.withStatus(org.zalando.problem.Status.CONFLICT)
			.withDetail("Cleanup cannot be applied to a request in status %s.".formatted(patronRequest.getStatus()))
			.with("patronRequestId", String.valueOf(patronRequest.getId()))
			.with("patronRequestStatus", String.valueOf(patronRequest.getStatus()))
			.build();
	}

	private static PatronRequest refuseUnlessOverridden(PatronRequest patronRequest, boolean override,
		String detail, Status offendingStatus) {

		if (override) {
			log.warn("Forced cleanup of patron request {} while item is out (status {}, last known {})",
				patronRequest.getId(), patronRequest.getStatus(), offendingStatus);

			return patronRequest;
		}

		throw Problem.builder()
			.withType(ERR_CLEANUP)
			.withTitle("Cannot clean up a request while the item is out")
			.withStatus(org.zalando.problem.Status.CONFLICT)
			.withDetail(detail)
			.with("patronRequestId", String.valueOf(patronRequest.getId()))
			// "status" is a reserved Problem property and throws if used here
			.with("patronRequestStatus", String.valueOf(patronRequest.getStatus()))
			.with("lastKnownItemOutStatus", String.valueOf(offendingStatus))
			.build();
	}

	// Transition errors are audited and swallowed inside the workflow service, so the status is the only
	// evidence of whether cleanup happened.
	static PatronRequest ensureCleanedUp(PatronRequest patronRequest) {
		if (CLEANED_UP.contains(patronRequest.getStatus())) {
			return patronRequest;
		}

		throw Problem.builder()
			.withType(ERR_CLEANUP)
			.withTitle("Cleanup failed")
			.withStatus(org.zalando.problem.Status.INTERNAL_SERVER_ERROR)
			.withDetail("The request is still %s after cleanup was attempted; see its audit log."
				.formatted(patronRequest.getStatus()))
			.with("patronRequestId", String.valueOf(patronRequest.getId()))
			.with("patronRequestStatus", String.valueOf(patronRequest.getStatus()))
			.build();
	}

	private static ThrowableProblem problem(org.zalando.problem.Status status, String title, String detail,
		UUID patronRequestId) {

		return Problem.builder()
			.withType(ERR_CLEANUP)
			.withTitle(title)
			.withStatus(status)
			.withDetail(detail)
			.with("patronRequestId", String.valueOf(patronRequestId))
			.build();
	}
}
