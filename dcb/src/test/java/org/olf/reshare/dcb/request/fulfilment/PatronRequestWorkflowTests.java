package org.olf.reshare.dcb.request.fulfilment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.utils.BackgroundExecutor;

import reactor.core.publisher.Mono;

public class PatronRequestWorkflowTests {
	@Test
	void shouldAttemptTransitionAsynchronously() {
		final var transition = mock(PatronRequestResolutionStateTransition.class);
		final var backgroundExecutor = mock(BackgroundExecutor.class);

		final var patronRequest = createPatronRequest();

		final var monoFromTransition = Mono.empty().then();

		when(transition.attempt(any())).thenReturn(monoFromTransition);

		final var stateTransitionDelay = Duration.ofSeconds(5);

		final var requestWorkflow = new PatronRequestWorkflow(transition,
			backgroundExecutor, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);
		
		verify(transition).attempt(any());

		verify(backgroundExecutor)
			.executeAsynchronously(eq(monoFromTransition), eq(stateTransitionDelay));
	}

	private static PatronRequest createPatronRequest() {
		return new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			UUID.randomUUID(), "pickupLocationCode", SUBMITTED_TO_DCB);
	}
}
