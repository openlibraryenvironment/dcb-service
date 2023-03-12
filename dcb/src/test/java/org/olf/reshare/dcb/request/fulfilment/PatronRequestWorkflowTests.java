package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

public class PatronRequestWorkflowTests {

	@Test
	void shouldCallPatronRequestStateTransition() {
		final var patronRequestResolutionStateTransition = mock(PatronRequestResolutionStateTransition.class);
		final var requestWorkflow = new PatronRequestWorkflow(patronRequestResolutionStateTransition);
		final var patronRequest = new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			UUID.randomUUID(), "pickupLocationCode", SUBMITTED_TO_DCB);

		when( patronRequestResolutionStateTransition.makeTransition(any()) )
			.thenAnswer(invocation -> Mono.just(patronRequest));

		requestWorkflow.initiate(patronRequest).block();
		verify(patronRequestResolutionStateTransition).makeTransition(any());
	}
}
