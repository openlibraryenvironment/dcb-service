package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.BeanProvider;
import reactor.core.publisher.Mono;

/** No database: the request is saved with who placed it, as the entry point decided. */
class PlacedByStaffRecordedTests {
	private PatronRequestRepository patronRequestRepository;
	private PatronRequestService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() {
		patronRequestRepository = mock(PatronRequestRepository.class);
		when(patronRequestRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		final var preflightChecksService = mock(PatronRequestPreflightChecksService.class);
		when(preflightChecksService.check(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		final var findOrCreatePatronService = mock(FindOrCreatePatronService.class);
		when(findOrCreatePatronService.findOrCreatePatron(any(), any(), any()))
			.thenReturn(Mono.just(Patron.builder().id(UUID.randomUUID()).build()));

		service = new PatronRequestService(patronRequestRepository,
			mock(PatronRequestWorkflowService.class), mock(PatronService.class),
			findOrCreatePatronService, preflightChecksService,
			mock(PatronRequestAuditRepository.class), mock(BeanProvider.class), mock(HostLmsService.class),
			mock(BibRepository.class), mock(AgencyRepository.class), mock(LocationService.class),
			new IntMessageService());
	}

	@Test
	void shouldRecordARequestStaffPlaced() {
		assertThat(savedPlacedByStaff(true), is(true));
	}

	@Test
	void shouldRecordARequestAPatronPlaced() {
		assertThat(savedPlacedByStaff(false), is(false));
	}

	@Test
	void shouldRecordNothingWhenWhoPlacedItIsUnknown() {
		assertThat(savedPlacedByStaff(null), is(nullValue()));
	}

	private Boolean savedPlacedByStaff(Boolean placedByStaff) {
		service.placePatronRequest(PlacePatronRequestCommand.builder()
			.citation(PlacePatronRequestCommand.Citation.builder().bibClusterId(UUID.randomUUID()).build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder().code("pickup").build())
			.requestor(PlacePatronRequestCommand.Requestor.builder().localId("p").localSystemCode("s").build())
			.isPlacedByStaff(placedByStaff)
			.build()).block();

		final var saved = ArgumentCaptor.forClass(PatronRequest.class);
		verify(patronRequestRepository).save(saved.capture());

		return saved.getValue().getPlacedByStaff();
	}
}
