package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

public class PatronRequestResolutionStateTransitionTests {

	@Test
	void shouldResolveRequest() {
		final var patronRequestResolutionService = mock(PatronRequestResolutionService.class);
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var patronRequestResolutionStateTransition = new PatronRequestResolutionStateTransition(patronRequestResolutionService,
			patronRequestRepository, supplierRequestRepository);

		final var patronRequest = new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			UUID.randomUUID(), "pickupLocationCode", RESOLVED);

		final var supplierRequest = new SupplierRequest(UUID.randomUUID(),
			patronRequest, UUID.randomUUID(), "holdingsAgencyCode");

		when(patronRequestResolutionService.resolvePatronRequest(any()))
			.thenAnswer(invocation -> Mono.just(supplierRequest));

		when(patronRequestRepository.update(any()))
			.thenAnswer(invocation -> Mono.just(patronRequest));

		when(supplierRequestRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(supplierRequest));

		patronRequestResolutionStateTransition.attempt(patronRequest).block();
		verify(patronRequestResolutionService).resolvePatronRequest(any());
		verify(patronRequestRepository).update(any());
		verify(supplierRequestRepository).save(any());
	}
}
