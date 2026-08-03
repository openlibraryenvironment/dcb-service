package org.olf.dcb.core.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.zalando.problem.ThrowableProblem;

import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

/**
 * The manual cleanup guard. Cleanup deletes the borrowing library's virtual item and bib, which orphans
 * the physical item if it is not back at the supplier yet - so the API refuses, but with a 409 the caller
 * can act on and an explicit override for the cases support genuinely needs.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestCleanupGuardTests {
	@Inject
	private PatronRequestController patronRequestController;

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"PICKUP_TRANSIT", "RECEIVED_AT_PICKUP", "READY_FOR_PICKUP", "LOANED", "RETURN_TRANSIT",
		"AWAITING_RETURN_TO_SUPPLIER"
	})
	void shouldRejectCleanupWithConflictWhileTheItemIsOut(Status status) {
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> patronRequestController.ensureValidStateForCleanupTransition(patronRequest, false));

		// 409, not the 500 an IllegalStateException produced - the caller can tell "you may not do this
		// yet" from "DCB fell over".
		assertThat("Should be reported as a conflict", problem.getStatus().getStatusCode(), is(409));
		assertThat("Should say why", problem.getDetail(), containsString("not back at the supplying library"));
		assertThat("Should name the offending status", problem.getDetail(), containsString(status.toString()));
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"PICKUP_TRANSIT", "RECEIVED_AT_PICKUP", "READY_FOR_PICKUP", "LOANED", "RETURN_TRANSIT",
		"AWAITING_RETURN_TO_SUPPLIER"
	})
	void shouldAllowCleanupWhileTheItemIsOutWhenExplicitlyForced(Status status) {
		// Support does occasionally need to clear a genuinely stuck request. The override exists so they
		// are not blocked, but it has to be asked for.
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.build();

		assertThat(patronRequestController.ensureValidStateForCleanupTransition(patronRequest, true),
			is(patronRequest));
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"SUBMITTED_TO_DCB", "PATRON_VERIFIED", "RESOLVED", "REQUEST_PLACED_AT_SUPPLYING_AGENCY",
		"CONFIRMED", "REQUEST_PLACED_AT_BORROWING_AGENCY", "REQUEST_PLACED_AT_PICKUP_AGENCY",
		"NO_ITEMS_SELECTABLE_AT_ANY_AGENCY", "ERROR"
	})
	void shouldAllowCleanupWhenNothingIsOut(Status status) {
		// Mirrors dcb-admin-ui's cleanupStatuses. ERROR is deliberately included - clearing up errored
		// requests is the main thing this endpoint is for.
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.build();

		assertThat(patronRequestController.ensureValidStateForCleanupTransition(patronRequest, false),
			is(patronRequest));
	}

	@Test
	void shouldRejectCleanupOfAnAlreadyCancelledRequest() {
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(Status.CANCELLED)
			.build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> patronRequestController.ensureValidStateForCleanupTransition(patronRequest, false));

		assertThat(problem.getStatus().getStatusCode(), is(409));
	}
}
