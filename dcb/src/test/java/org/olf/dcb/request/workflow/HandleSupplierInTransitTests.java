package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import reactor.core.publisher.Mono;

class HandleSupplierInTransitTests {
	@Test
	void appliesWhenSupplierItemIsInTransit() {
		final var transition = transitionWithMocks();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(new PatronRequest()
				.setId(UUID.randomUUID())
				.setStatus(REQUEST_PLACED_AT_BORROWING_AGENCY))
			.setSupplierRequest(new SupplierRequest()
				.setLocalItemStatus(ITEM_TRANSIT));

		assertThat(transition.isApplicableFor(context), is(true));
	}

	@Test
	void transitReactionUpdatesBorrowerAndPickupSystemsForPickupAnywhere() {
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var patronSystem = mock(HostLmsClient.class);
		final var pickupSystem = mock(HostLmsClient.class);
		final var transition = new HandleSupplierInTransit(
			supplierRequestRepository,
			patronRequestRepository,
			hostLmsService,
			mock(PatronRequestAuditService.class));
		final var patronRequest = new PatronRequest()
			.setId(UUID.randomUUID())
			.setStatus(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.setActiveWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.setLocalItemId("borrower-item")
			.setLocalBibId("borrower-bib")
			.setLocalHoldingId("borrower-holding")
			.setLocalRequestId("borrower-request")
			.setPickupItemId("pickup-item")
			.setPickupBibId("pickup-bib")
			.setPickupHoldingId("pickup-holding")
			.setPickupRequestId("pickup-request");
		final var supplierRequest = new SupplierRequest()
			.setLocalItemId("supplier-item")
			.setLocalItemStatus(ITEM_TRANSIT);
		final var context = new RequestWorkflowContext()
			.setPatronSystemCode("borrower-host")
			.setPickupSystem(pickupSystem)
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(hostLmsService.getClientFor("borrower-host"))
			.thenReturn(Mono.just(patronSystem));
		when(patronSystem.updateItemStatus(any(HostLmsItem.class), eq(TRANSIT)))
			.thenReturn(Mono.just("OK"));
		when(pickupSystem.updateItemStatus(any(HostLmsItem.class), eq(TRANSIT)))
			.thenReturn(Mono.just("OK"));
		when(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));
		when(patronRequestRepository.saveOrUpdate(patronRequest))
			.thenReturn(Mono.just(patronRequest));

		transition.attempt(context).block();

		assertThat(patronRequest.getStatus(), is(PICKUP_TRANSIT));
		final var borrowerItem = ArgumentCaptor.forClass(HostLmsItem.class);
		verify(patronSystem).updateItemStatus(borrowerItem.capture(), eq(TRANSIT));
		assertThat(borrowerItem.getValue().getLocalId(), is("borrower-item"));
		assertThat(borrowerItem.getValue().getLocalRequestId(),
			is("borrower-request"));

		final var pickupItem = ArgumentCaptor.forClass(HostLmsItem.class);
		verify(pickupSystem).updateItemStatus(pickupItem.capture(), eq(TRANSIT));
		assertThat(pickupItem.getValue().getLocalId(), is("pickup-item"));
		assertThat(pickupItem.getValue().getLocalRequestId(),
			is("pickup-request"));
		verify(supplierRequestRepository).saveOrUpdate(supplierRequest);
		verify(patronRequestRepository).saveOrUpdate(patronRequest);
	}

	private static HandleSupplierInTransit transitionWithMocks() {
		return new HandleSupplierInTransit(
			mock(SupplierRequestRepository.class),
			mock(PatronRequestRepository.class),
			mock(HostLmsService.class),
			mock(PatronRequestAuditService.class));
	}
}
