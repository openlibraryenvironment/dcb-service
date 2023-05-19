package org.olf.reshare.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

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
