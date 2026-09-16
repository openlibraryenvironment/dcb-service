package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.zalando.problem.ThrowableProblem;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ManualCleanupServiceTests {
	private PatronRequestService patronRequestService;
	private PatronRequestWorkflowService workflowService;
	private ManualCleanupService manualCleanupService;

	@BeforeEach
	void beforeEach() {
		patronRequestService = mock(PatronRequestService.class);
		workflowService = mock(PatronRequestWorkflowService.class);

		manualCleanupService = new ManualCleanupService(patronRequestService, workflowService,
			new CleanupPatronRequestTransition(mock(PatronRequestAuditService.class)));
	}

	@Test
	void unknownRequestIsNotFound() {
		final var id = UUID.randomUUID();
		when(patronRequestService.findById(id)).thenReturn(Mono.empty());

		expectProblem(manualCleanupService.cleanup(id, false), 404);
		verifyNothingRan();
	}

	@Test
	void itemOutWithoutOverrideIsAConflictAndNothingRuns() {
		final var request = request(Status.PICKUP_TRANSIT);
		when(patronRequestService.findById(request.getId())).thenReturn(Mono.just(request));

		expectProblem(manualCleanupService.cleanup(request.getId(), false), 409);
		verifyNothingRan();
	}

	@Test
	void cleanupFromANonSourceStatusIsAConflictAndNothingRuns() {
		final var request = request(Status.HANDED_OFF_AS_LOCAL);
		when(patronRequestService.findById(request.getId())).thenReturn(Mono.just(request));

		expectProblem(manualCleanupService.cleanup(request.getId(), false), 409);
		verifyNothingRan();
	}

	@Test
	void cleanupThatDoesNotCompleteIsAServerError() {
		final var request = request(Status.ERROR);
		when(patronRequestService.findById(request.getId())).thenReturn(Mono.just(request));
		when(workflowService.progressUsing(any(PatronRequest.class), any(PatronRequestStateTransition.class)))
			.thenReturn(Mono.just(request));

		expectProblem(manualCleanupService.cleanup(request.getId(), false), 500);
	}

	@Test
	void cleanupThatProgressesToAnEmptyResultIsJudgedByTheStoredStatus() {
		final var request = request(Status.ERROR);
		final var finalised = request(Status.FINALISED).setId(request.getId());
		when(patronRequestService.findById(request.getId()))
			.thenReturn(Mono.just(request), Mono.just(finalised));
		when(workflowService.progressUsing(any(PatronRequest.class), any(PatronRequestStateTransition.class)))
			.thenReturn(Mono.empty());

		StepVerifier.create(manualCleanupService.cleanup(request.getId(), false))
			.expectNext(request.getId())
			.verifyComplete();
	}

	@Test
	void forcedCleanupOfAParkedRequestSucceeds() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		final var completed = request(Status.COMPLETED).setId(request.getId());
		when(patronRequestService.findById(request.getId()))
			.thenReturn(Mono.just(request), Mono.just(completed));
		when(workflowService.progressUsing(any(PatronRequest.class), any(PatronRequestStateTransition.class)))
			.thenReturn(Mono.just(completed));

		StepVerifier.create(manualCleanupService.cleanup(request.getId(), true))
			.expectNext(request.getId())
			.verifyComplete();
	}

	private static PatronRequest request(Status status) {
		return PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(status)
			.build();
	}

	private void verifyNothingRan() {
		verify(workflowService, never())
			.progressUsing(any(PatronRequest.class), any(PatronRequestStateTransition.class));
	}

	private static void expectProblem(Mono<UUID> result, int statusCode) {
		StepVerifier.create(result)
			.expectErrorSatisfies(error -> {
				assertThat(error, instanceOf(ThrowableProblem.class));
				assertThat(((ThrowableProblem) error).getStatus().getStatusCode(), is(statusCode));
			})
			.verify();
	}
}
