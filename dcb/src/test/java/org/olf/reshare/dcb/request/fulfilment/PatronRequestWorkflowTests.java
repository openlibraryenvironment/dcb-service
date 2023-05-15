package org.olf.reshare.dcb.request.fulfilment;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.utils.BackgroundExecutor;

import reactor.core.publisher.Mono;

public class PatronRequestWorkflowTests {

	@Test
	public void testPlacePatronRequestAtSupplyingAgencyStateTransitionCalledAfterFirstTransition() {
		// Create a PatronRequest with status SUBMITTED_TO_DCB
		PatronRequest patronRequestSubmitted = createPatronRequestWithStatus(SUBMITTED_TO_DCB);

		// Create a PatronRequest with status RESOLVED
		PatronRequest patronRequestResolved = createPatronRequestWithStatus(RESOLVED);

		// Create a mock PatronRequestResolutionStateTransition
		PatronRequestResolutionStateTransition resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		when(resolutionTransition.attempt(eq(patronRequestSubmitted)))
			.thenAnswer(invocation ->  Mono.just(patronRequestResolved));

		// Create a mock PlacePatronRequestAtSupplyingAgencyStateTransition
		PlacePatronRequestAtSupplyingAgencyStateTransition placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);
		when(placeTransition.attempt(eq(patronRequestResolved)))
			.thenReturn(Mono.empty());

		// Create a PatronRequestWorkflow instance
		PatronRequestWorkflow workflow = new PatronRequestWorkflow(resolutionTransition, placeTransition, Duration.ofSeconds(1));

		// Call initiate method
		workflow.initiate(patronRequestSubmitted);

		// Wait for the transitions to complete
		await().atMost(10, TimeUnit.SECONDS).until(() -> {
			verify(resolutionTransition, times(1)).attempt(patronRequestSubmitted);
			verify(placeTransition, times(1)).attempt(patronRequestResolved);
			return true;
		});

		// Verify that initiate method is called after the first transition
		verify(resolutionTransition, times(1)).attempt(patronRequestSubmitted);
		verify(placeTransition, times(1)).attempt(patronRequestResolved);
	}

	@Test
	void shouldAttemptTransitionAsynchronouslyWhenStatusSubmittedToDcb() {
		final var resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		final var placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);

		final var patronRequest = createPatronRequestWithStatus(SUBMITTED_TO_DCB);

		final var monoFromTransition = Mono.empty().then();

		when(resolutionTransition.attempt(patronRequest))
			.thenAnswer(invocation ->  monoFromTransition );

		final var stateTransitionDelay = Duration.ofSeconds(1);

		final var requestWorkflow = new PatronRequestWorkflow(resolutionTransition,
			placeTransition, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);

		verify(resolutionTransition).attempt(patronRequest);
	}


	@Test
	void shouldNotAttemptTransitionWhenUnrecognisedStatus() {
		final var resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		final var placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);
		final var UNRECOGNISED_STATUS = "UNRECOGNISED_STATUS";

		final var patronRequest = createPatronRequestWithStatus(UNRECOGNISED_STATUS);

		final var monoFromTransition = Mono.empty().then();

		when(resolutionTransition.attempt(patronRequest))
			.thenAnswer(invocation ->  monoFromTransition );

		final var stateTransitionDelay = Duration.ofSeconds(0);

		final var requestWorkflow = new PatronRequestWorkflow(resolutionTransition,
			placeTransition, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);

		verify(resolutionTransition, times(0)).attempt(patronRequest);
	}

	private static PatronRequest createPatronRequestWithStatus(String status) {
		return new PatronRequest(UUID.randomUUID(), null, null,
			new Patron(), UUID.randomUUID(), "pickupLocationCode",
			status, null, null);
	}
}
