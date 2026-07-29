package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CleanupPatronRequestTransitionTests {
	@Test
	void recordsUnknownOutcomeWhenManualCleanupHasNoKnownOutcome() {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.ERROR)
			.build();

		attemptCleanup(patronRequest);

		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.COMPLETED));
		assertThat(patronRequest.getOutcome(), is(PatronRequest.Outcome.UNKNOWN));
	}

	@Test
	void preservesKnownOutcomeDuringManualCleanup() {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY)
			.outcome(PatronRequest.Outcome.NOT_SUPPLIED)
			.build();

		attemptCleanup(patronRequest);

		assertThat(patronRequest.getOutcome(), is(PatronRequest.Outcome.NOT_SUPPLIED));
	}

	private static void attemptCleanup(PatronRequest patronRequest) {
		final var auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));
		final var context = new RequestWorkflowContext().setPatronRequest(patronRequest);

		StepVerifier.create(new CleanupPatronRequestTransition(auditService).attempt(context))
			.expectNext(context)
			.verifyComplete();
	}
}
