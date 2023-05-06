package org.olf.reshare.dcb.request.fulfilment;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.utils.BackgroundExecutor;

import reactor.core.publisher.Mono;

public class PatronRequestWorkflowTests {
	@Test
	void shouldAttemptTransitionAsynchronouslyWhenStatusSubmittedToDcb() {
		final var transition = mock(PatronRequestResolutionStateTransition.class);
		final var backgroundExecutor = mock(BackgroundExecutor.class);

		final var patronRequest = createPatronRequestWithStatus(SUBMITTED_TO_DCB);

		final var monoFromTransition = Mono.empty().then();

		when(transition.attempt(patronRequest)).thenReturn(monoFromTransition);

		final var stateTransitionDelay = Duration.ofSeconds(1);

		final var requestWorkflow = new PatronRequestWorkflow(transition,
			backgroundExecutor, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);
		
		verify(transition).attempt(patronRequest);

		verify(backgroundExecutor)
			.executeAsynchronously(eq(monoFromTransition), eq(stateTransitionDelay));
	}

	@Test
	void shouldNotAttemptTransitionWhenUnrecognisedStatus() {
		final var transition = mock(PatronRequestResolutionStateTransition.class);
		final var backgroundExecutor = mock(BackgroundExecutor.class);
		final var UNRECOGNISED_STATUS = "UNRECOGNISED_STATUS";

		final var patronRequest = createPatronRequestWithStatus(UNRECOGNISED_STATUS);

		final var monoFromTransition = Mono.empty().then();

		when(transition.attempt(patronRequest)).thenReturn(monoFromTransition);

		final var stateTransitionDelay = Duration.ofSeconds(0);

		final var requestWorkflow = new PatronRequestWorkflow(transition,
			backgroundExecutor, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);

		verify(transition, times(0)).attempt(patronRequest);

		verify(backgroundExecutor, times(0))
			.executeAsynchronously(eq(monoFromTransition), eq(stateTransitionDelay));
	}

	private static PatronRequest createPatronRequestWithStatus(String status) {
		return new PatronRequest(UUID.randomUUID(), null, null,
			new Patron(), "patronAgencyCode",
			UUID.randomUUID(), "pickupLocationCode", status, null);
	}
}
