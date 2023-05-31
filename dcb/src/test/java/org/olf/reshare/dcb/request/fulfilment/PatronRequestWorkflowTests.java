package org.olf.reshare.dcb.request.fulfilment;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/*
 * please note - there is now some divergence between the state model as it exists in DCB and the way the
 * mocks set up a simplified chain here. This is fine if the intent of this test is to verify that the 
 * workflow engine works the way we expect -- specifically, is it capable of applying repeated transformations
 * until a terminal state or intermediate state is reached. The state model created by the mocks here however
 * is not the same as the state model implemented over in the main codebase, and this test is not an
 * exercise of the DCB state machine itself.
 */
class PatronRequestWorkflowTests {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflowTests.class);

	@Test
	void testPlacePatronRequestAtSupplyingAgencyStateTransitionCalledAfterFirstTransition() {
		log.debug("testPlacePatronRequestAtSupplyingAgencyStateTransitionCalledAfterFirstTransition()");

		// Create a PatronRequest with status SUBMITTED_TO_DCB
		PatronRequest patronRequestSubmitted = createPatronRequestWithStatus(SUBMITTED_TO_DCB);

		// Create a PatronRequest with status RESOLVED
		PatronRequest patronRequestResolved = createPatronRequestWithStatus(RESOLVED);

		// Create a mock PatronRequestResolutionStateTransition
		PatronRequestResolutionStateTransition resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		when(resolutionTransition.attempt(eq(patronRequestSubmitted))) .thenAnswer(invocation ->  Mono.just(patronRequestResolved));
		when(resolutionTransition.getGuardCondition()).thenReturn("state=="+SUBMITTED_TO_DCB);

		// Create a mock PlacePatronRequestAtSupplyingAgencyStateTransition
		PlacePatronRequestAtSupplyingAgencyStateTransition placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);
		when(placeTransition.attempt(eq(patronRequestResolved))).thenReturn(Mono.empty());
		when(placeTransition.getGuardCondition()).thenReturn("state=="+RESOLVED);

		// Create a PatronRequestWorkflow instance
		final var l = Arrays.asList(resolutionTransition, placeTransition);
		PatronRequestWorkflow workflow = new PatronRequestWorkflow(l, Duration.ofSeconds(1));

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
		log.debug("shouldAttemptTransitionAsynchronouslyWhenStatusSubmittedToDcb()");

		final var resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		final var placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);

		final var patronRequest = createPatronRequestWithStatus(SUBMITTED_TO_DCB);

		final var monoFromTransition = Mono.empty().then();

		when(resolutionTransition.attempt(patronRequest)) .thenAnswer(invocation ->  monoFromTransition );

		when(resolutionTransition.getGuardCondition()) .thenReturn("state=="+SUBMITTED_TO_DCB);
		when(placeTransition.getGuardCondition()).thenReturn("state=="+RESOLVED);

		final var stateTransitionDelay = Duration.ofSeconds(1);

		final var l = Arrays.asList(resolutionTransition, placeTransition);
		final var requestWorkflow = new PatronRequestWorkflow(l, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);

		verify(resolutionTransition).attempt(patronRequest);
	}

	@Test
	void shouldNotAttemptTransitionWhenUnrecognisedStatus() {
		log.debug("shouldNotAttemptTransitionWhenUnrecognisedStatus()");

		final var resolutionTransition = mock(PatronRequestResolutionStateTransition.class);
		final var placeTransition = mock(PlacePatronRequestAtSupplyingAgencyStateTransition.class);
		final var UNRECOGNISED_STATUS = "UNRECOGNISED_STATUS";

		final var patronRequest = createPatronRequestWithStatus(UNRECOGNISED_STATUS);

		final var monoFromTransition = Mono.empty().then();

		when(resolutionTransition.attempt(patronRequest)) .thenAnswer(invocation ->  monoFromTransition );
		when(resolutionTransition.getGuardCondition()) .thenReturn("state=="+SUBMITTED_TO_DCB);
		when(placeTransition.getGuardCondition()).thenReturn("state=="+RESOLVED);

		final var stateTransitionDelay = Duration.ofSeconds(0);

		final var l = Arrays.asList(resolutionTransition,placeTransition);
		final var requestWorkflow = new PatronRequestWorkflow(l, stateTransitionDelay);

		requestWorkflow.initiate(patronRequest);

		verify(resolutionTransition, times(0)).attempt(patronRequest);
	}

	private static PatronRequest createPatronRequestWithStatus(String status) {
		return PatronRequest.builder()
			.statusCode(status)
			.build();
	}
}
