package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import reactor.core.publisher.Mono;

class FinaliseRequestTransitionWithoutVirtualPatronTests {
	@Test
	@SuppressWarnings("unchecked")
	void finalisesWhenSupplierProtocolDoesNotCreateVirtualPatron() {
		final var auditService = mock(PatronRequestAuditService.class);
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var pickupAgencyService = mock(PickupAgencyService.class);
		final var cleanupService = mock(CleanupService.class);
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.COMPLETED)
			.outcome(PatronRequest.Outcome.SUPPLIED)
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("supplier-host")
			.localId("supplier-request-1")
			.build();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(cleanupService.cleanup(context)).thenReturn(Mono.just(context));
		when(borrowingAgencyService.getItem(patronRequest)).thenReturn(Mono.empty());
		when(supplyingAgencyService.getRequest(anyString(), any())).thenReturn(Mono.empty());
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString(), anyMap()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));

		final var transition = new FinaliseRequestTransition(
			auditService,
			supplyingAgencyService,
			borrowingAgencyService,
			pickupAgencyService,
			cleanupService);

		assertThat(singleValueFrom(transition.attempt(context)), is(context));
		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.FINALISED));
		assertThat(patronRequest.getOutcome(), is(PatronRequest.Outcome.SUPPLIED));
		verify(cleanupService).cleanup(context);
		verify(borrowingAgencyService).getItem(patronRequest);
		verify(supplyingAgencyService, never()).getPatron(context);
		final var requestCaptor = ArgumentCaptor.forClass(HostLmsRequest.class);
		verify(supplyingAgencyService).getRequest(
			eq("supplier-host"), requestCaptor.capture());
		assertThat(requestCaptor.getValue().getLocalId(),
			is("supplier-request-1"));
		final ArgumentCaptor<Map<String, Object>> auditDataCaptor =
			ArgumentCaptor.forClass(Map.class);
		verify(auditService).addAuditEntry(
			eq(patronRequest), eq("Clean up result"), auditDataCaptor.capture());
		assertThat(auditDataCaptor.getValue().get("VirtualPatron"),
			is("Not created by supplier protocol"));
		verifyNoInteractions(pickupAgencyService);
	}
}
