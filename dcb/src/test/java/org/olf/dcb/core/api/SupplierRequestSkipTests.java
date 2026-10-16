package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.TrackingService;

import io.micronaut.http.HttpStatus;
import reactor.core.publisher.Mono;

class SupplierRequestSkipTests {
	private SupplyingAgencyService supplyingAgencyService;
	private SupplierRequestRepository supplierRequestRepository;
	private RequestWorkflowContextHelper ctxHelper;
	private TrackingService trackingService;
	private SupplierRequestController controller;

	@BeforeEach
	void beforeEach() {
		supplyingAgencyService = mock(SupplyingAgencyService.class);
		supplierRequestRepository = mock(SupplierRequestRepository.class);
		ctxHelper = mock(RequestWorkflowContextHelper.class);
		trackingService = mock(TrackingService.class);

		controller = new SupplierRequestController(mock(SupplierRequestService.class),
			supplyingAgencyService, supplierRequestRepository, ctxHelper, trackingService);
	}

	@ParameterizedTest
	@EnumSource(value = PatronRequest.Status.class, names = {"REQUEST_PLACED_AT_SUPPLYING_AGENCY",
		"CONFIRMED", "REQUEST_PLACED_AT_BORROWING_AGENCY", "REQUEST_PLACED_AT_PICKUP_AGENCY"})
	void skipsTheActiveSupplierBeforeTheItemShips(PatronRequest.Status status) {
		final var supplierRequest = activeSupplierRequestFor(status);

		final var response = controller.skipSupplierAndReResolve(supplierRequest.getId(), null).block();

		assertThat(response.getStatus(), is(HttpStatus.OK));
		verify(supplyingAgencyService).cancelHold(any());
		verify(trackingService).forceUpdate(supplierRequest.getPatronRequest().getId());
	}

	@ParameterizedTest
	@EnumSource(value = PatronRequest.Status.class, names = {"PICKUP_TRANSIT", "RECEIVED_AT_PICKUP",
		"READY_FOR_PICKUP", "LOANED", "RETURN_TRANSIT", "AWAITING_RETURN_TO_SUPPLIER", "COMPLETED",
		"FINALISED", "CANCELLED", "ERROR", "SUBMITTED_TO_DCB", "RESOLVED"})
	void refusesOnceTheItemHasShippedOrBeforeThereIsASupplierHold(PatronRequest.Status status) {
		final var supplierRequest = activeSupplierRequestFor(status);

		final var response = controller.skipSupplierAndReResolve(supplierRequest.getId(), null).block();

		assertThat(response.getStatus(), is(HttpStatus.CONFLICT));
		verify(supplyingAgencyService, never()).cancelHold(any());
		verify(trackingService, never()).forceUpdate(any());
	}

	@Test
	void refusesASupplierRequestThatIsNotTheActiveOne() {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		final var earlier = SupplierRequest.builder().id(UUID.randomUUID()).patronRequest(patronRequest).build();
		final var active = SupplierRequest.builder().id(UUID.randomUUID()).patronRequest(patronRequest).build();

		when(supplierRequestRepository.findById(earlier.getId())).thenReturn(Mono.just(earlier));
		when(ctxHelper.fetchWorkflowContext(patronRequest.getId())).thenReturn(Mono.just(
			new RequestWorkflowContext().setPatronRequest(patronRequest).setSupplierRequest(active)));

		final var response = controller.skipSupplierAndReResolve(earlier.getId(), null).block();

		assertThat(response.getStatus(), is(HttpStatus.CONFLICT));
		verify(supplyingAgencyService, never()).cancelHold(any());
	}

	private SupplierRequest activeSupplierRequestFor(PatronRequest.Status status) {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(status)
			.build();

		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(supplierRequestRepository.findById(supplierRequest.getId())).thenReturn(Mono.just(supplierRequest));
		when(ctxHelper.fetchWorkflowContext(patronRequest.getId())).thenReturn(Mono.just(context));
		when(supplyingAgencyService.cancelHold(any())).thenReturn(Mono.just(context));
		when(trackingService.forceUpdate(patronRequest.getId())).thenReturn(Mono.just(patronRequest.getId()));

		return supplierRequest;
	}
}
