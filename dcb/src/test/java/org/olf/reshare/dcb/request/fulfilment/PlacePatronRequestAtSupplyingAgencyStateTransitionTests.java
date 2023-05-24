package org.olf.reshare.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PlacePatronRequestAtSupplyingAgencyStateTransitionTests {
	@Mock
	private SupplyingAgencyService supplyingAgencyService;
	@Mock
	private PatronRequestRepository patronRequestRepository;
	@InjectMocks
	PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition;

	@Test
	void testAttempt() {
		// Arrange
		PatronRequest patronRequest = new PatronRequest();
		PatronRequest updatedPatronRequest = new PatronRequest();

		when(supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest))
			.thenAnswer(invocation ->  Mono.just(updatedPatronRequest));

		when(patronRequestRepository.update(updatedPatronRequest))
			.thenAnswer(invocation -> Mono.just(updatedPatronRequest));

		// Act
		var result = placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block();

		// Assert
		assertThat("Patron request was expected.", result, is(updatedPatronRequest));
		verify(supplyingAgencyService).placePatronRequestAtSupplyingAgency(any());
	}
}
