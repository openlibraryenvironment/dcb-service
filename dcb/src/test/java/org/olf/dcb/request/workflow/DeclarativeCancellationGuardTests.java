package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

class DeclarativeCancellationGuardTests {
	@Test
	void declarativeSupplierCleanupIsAuditedWithoutImperativeCancellation() {
		final var auditService = mock(PatronRequestAuditService.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		final var pickupAgencyService = mock(PickupAgencyService.class);
		final var transition = new CancelledPatronRequestTransition(
			auditService,
			hostLmsService,
			supplierRequestRepository,
			supplyingAgencyService,
			pickupAgencyService);
		final var patronRequest = new PatronRequest()
			.setId(UUID.randomUUID())
			.setStatus(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
			.setLocalRequestStatus(HOLD_MISSING);
		final var supplierRequest = new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalId("supplier-remote-request")
			.setLocalStatus("PLACED")
			.setProtocol("iso18626");
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(auditService.addAuditEntry(eq(patronRequest), anyString(), anyMap()))
			.thenReturn(Mono.empty());

		transition.attempt(context).block();

		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.CANCELLED));
		verify(supplyingAgencyService, never()).cancelHold(context);
		verify(hostLmsService, never()).getClientFor("supplier-host");
		verify(auditService).addAuditEntry(eq(patronRequest),
			eq("Declarative supplier cancellation is not implemented; skipping imperative supplier cleanup."),
			anyMap());
		verify(auditService).addAuditEntry(eq(patronRequest),
			eq("Declarative supplier cancellation verification is not implemented; skipping imperative supplier verification."),
			anyMap());
	}
}
