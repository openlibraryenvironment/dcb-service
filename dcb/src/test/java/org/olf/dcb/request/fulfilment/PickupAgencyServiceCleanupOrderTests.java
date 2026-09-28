package org.olf.dcb.request.fulfilment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;

import reactor.core.publisher.Mono;

class PickupAgencyServiceCleanupOrderTests {
	@Test
	void shouldCancelThePickupHoldBeforeDeletingTheItemItPointsAt() {
		final var pickupSystem = mock(HostLmsClient.class);

		when(pickupSystem.getRequest(any(HostLmsRequest.class))).thenReturn(Mono.just(
			HostLmsRequest.builder().localId("pickup-hold").status(HostLmsRequest.HOLD_CONFIRMED).build()));
		when(pickupSystem.getItem(any(HostLmsItem.class))).thenReturn(Mono.just(
			HostLmsItem.builder().localId("pickup-item").build()));
		when(pickupSystem.deleteHold(any(DeleteCommand.class))).thenReturn(Mono.just("OK"));
		when(pickupSystem.deleteItem(any(DeleteCommand.class))).thenReturn(Mono.just("OK"));
		when(pickupSystem.deleteBib("pickup-bib")).thenReturn(Mono.just("OK"));

		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
			.pickupRequestId("pickup-hold")
			.pickupPatronId("pickup-patron")
			.pickupItemId("pickup-item")
			.pickupHoldingId("pickup-holding")
			.pickupBibId("pickup-bib")
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setPickupSystem(pickupSystem)
			.setPickupSystemCode("PICKUP");

		new PickupAgencyService(mock(PatronRequestAuditService.class)).cleanUp(context).block();

		final var order = inOrder(pickupSystem);
		order.verify(pickupSystem).deleteHold(any(DeleteCommand.class));
		order.verify(pickupSystem).deleteItem(any(DeleteCommand.class));
		order.verify(pickupSystem).deleteBib("pickup-bib");
	}
}
