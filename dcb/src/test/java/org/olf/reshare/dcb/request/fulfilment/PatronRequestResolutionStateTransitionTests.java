package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.request.resolution.Resolution;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static java.time.Instant.now;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.*;

class PatronRequestResolutionStateTransitionTests {
	private final PatronRequestResolutionService patronRequestResolutionService
		= mock(PatronRequestResolutionService.class);

	private final PatronRequestRepository patronRequestRepository
		= mock(PatronRequestRepository.class);

	private final SupplierRequestRepository supplierRequestRepository
		= mock(SupplierRequestRepository.class);

	private final PatronRequestResolutionStateTransition patronRequestResolutionStateTransition
		= new PatronRequestResolutionStateTransition(patronRequestResolutionService,
		patronRequestRepository, supplierRequestRepository);

	@Test
	void shouldSavePatronRequestAndSupplierRequestWhenResolvedToSupplierRequest() {
		final var patronRequestId = randomUUID();

		final var initialPatronRequest = createPatronRequest(patronRequestId,
			SUBMITTED_TO_DCB);

		// Patron request should only be resolved after resolution has happened
		final var resolvedPatronRequest = createPatronRequest(patronRequestId,
			RESOLVED);

		final var supplierRequest = new SupplierRequest(randomUUID(),
			resolvedPatronRequest, "itemId", "itemBarcode", "ItemLocationCode",
			"holdingsAgencyCode", null, null, null);

		when(patronRequestResolutionService.resolvePatronRequest(initialPatronRequest))
			.thenAnswer(invocation -> Mono.just(
				new Resolution(resolvedPatronRequest, Optional.of(supplierRequest))));

		when(patronRequestRepository.update(resolvedPatronRequest))
			.thenAnswer(invocation -> Mono.just(resolvedPatronRequest));

		when(supplierRequestRepository.save(supplierRequest))
			.thenAnswer(invocation -> Mono.just(supplierRequest));

		patronRequestResolutionStateTransition.attempt(initialPatronRequest).block();

		verify(patronRequestResolutionService).resolvePatronRequest(initialPatronRequest);

		verify(patronRequestRepository).update(resolvedPatronRequest);
		verify(supplierRequestRepository).save(supplierRequest);
	}

	@Test
	void shouldNotSaveSupplierRequestWhenResolvedToNoItemsAvailable() {
		final var patronRequestId = randomUUID();

		final var initialPatronRequest = createPatronRequest(patronRequestId,
			SUBMITTED_TO_DCB);

		// Patron request should only be resolved after resolution has happened
		final var resolvedPatronRequest = createPatronRequest(patronRequestId,
			NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);

		final var supplierRequest = new SupplierRequest(randomUUID(),
			resolvedPatronRequest, "itemId", "itemBarcode", "ItemLocationCode",
			"holdingsAgencyCode", null, null, null);

		when(patronRequestResolutionService.resolvePatronRequest(initialPatronRequest))
			.thenAnswer(invocation -> Mono.just(
				new Resolution(resolvedPatronRequest, empty())));

		when(patronRequestRepository.update(resolvedPatronRequest))
			.thenAnswer(invocation -> Mono.just(resolvedPatronRequest));

		patronRequestResolutionStateTransition.attempt(initialPatronRequest).block();

		verify(patronRequestResolutionService).resolvePatronRequest(initialPatronRequest);

		verify(patronRequestRepository).update(resolvedPatronRequest);
		verify(supplierRequestRepository, never()).save(supplierRequest);
	}

	private static PatronRequest createPatronRequest(UUID id, String status) {
		return new PatronRequest(id, now(), now(),
			new Patron(), randomUUID(), "pickupLocationCode",
			status,null,null);
	}
}
