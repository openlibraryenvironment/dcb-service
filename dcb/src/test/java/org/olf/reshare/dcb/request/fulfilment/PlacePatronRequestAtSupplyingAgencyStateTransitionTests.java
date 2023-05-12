package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.olf.reshare.dcb.core.model.*;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

class PlacePatronRequestAtSupplyingAgencyStateTransitionTests {
	@Mock
	private SupplyingAgencyService supplyingAgencyService;
	@Mock
	private PatronRequestRepository patronRequestRepository;
	@InjectMocks
	PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition;

	@BeforeEach
	void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	void testAttempt() {
		// Arrange
		PatronRequest patronRequest = new PatronRequest();

		when(supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest))
			.thenAnswer(invocation ->  Mono.just(patronRequest));

		when(patronRequestRepository.update(patronRequest))
			.thenAnswer(invocation -> Mono.just(patronRequest));

		// Act
		var result = placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest).block();

		// Assert
		assertThat("Patron request was expected.", result, is(patronRequest));
		verify(supplyingAgencyService).placePatronRequestAtSupplyingAgency(any());
	}
}
