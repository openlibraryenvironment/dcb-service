package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;
import org.junit.jupiter.api.Test;
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
	void finalisesWhenSupplierProtocolDoesNotCreateVirtualPatron() {
		final var auditService = mock(PatronRequestAuditService.class);
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var cleanupService = mock(CleanupService.class);
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.COMPLETED)
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("supplier-host")
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
			mock(PickupAgencyService.class),
			cleanupService);

		assertThat(singleValueFrom(transition.attempt(context)), is(context));
		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.FINALISED));
		verify(supplyingAgencyService, never()).getPatron(context);
	}
}
