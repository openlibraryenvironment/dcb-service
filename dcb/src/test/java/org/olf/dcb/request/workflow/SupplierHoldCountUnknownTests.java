package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class SupplierHoldCountUnknownTests {
	private static final String BORROWER = "BORROWING-SYSTEM";
	private static final String SUPPLIER = "SUPPLYING-SYSTEM";

	@Test
	void shouldPreventRenewalAndKeepTheKnownCountWhenTheSupplierCannotReportOne() {
		final var supplierClient = mock(HostLmsClient.class);
		when(supplierClient.getItem(any())).thenReturn(Mono.just(HostLmsItem.builder()
			.localId("supplier-item-1")
			.holdCount(null)
			.build()));

		final var borrowerClient = mock(HostLmsClient.class);
		when(borrowerClient.preventRenewalOnLoan(any())).thenReturn(Mono.empty());

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(supplierClient));
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrowerClient));

		final var auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class)))
			.thenReturn(Mono.empty());

		final var patronRequestRepository = mock(PatronRequestRepository.class);
		when(patronRequestRepository.saveOrUpdate(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		final var supplierRequestRepository = mock(SupplierRequestRepository.class);

		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.localId("supplier-request-1")
			.localItemId("supplier-item-1")
			.localHoldCount(1)
			.hostLmsCode(SUPPLIER)
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(PatronRequest.Status.LOANED)
				.build())
			.setPatronSystemCode(BORROWER)
			.setSupplierRequest(supplierRequest);

		new HandleSupplierHoldDetected(patronRequestRepository, supplierRequestRepository,
			auditService, hostLmsService).attempt(context).block();

		verify(borrowerClient).preventRenewalOnLoan(any());
		verify(supplierRequestRepository, never()).saveOrUpdate(any());
		assertThat(supplierRequest.getLocalHoldCount(), is(1));
	}

	@Test
	void shouldSayTheSupplierItemIsGoneWhenTheSupplierReportsItMissing() {
		shouldPreventRenewalSayingTheItemIsGone(Mono.just(HostLmsItem.builder()
			.localId("supplier-item-1")
			.status(HostLmsItem.ITEM_MISSING)
			.build()));
	}

	@Test
	void shouldSayTheSupplierItemIsGoneWhenTheSupplierAnswersWithNothing() {
		shouldPreventRenewalSayingTheItemIsGone(Mono.empty());
	}

	private void shouldPreventRenewalSayingTheItemIsGone(Mono<HostLmsItem> supplierAnswer) {
		final var supplierClient = mock(HostLmsClient.class);
		when(supplierClient.getItem(any())).thenReturn(supplierAnswer);

		final var borrowerClient = mock(HostLmsClient.class);
		when(borrowerClient.preventRenewalOnLoan(any())).thenReturn(Mono.empty());

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(supplierClient));
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrowerClient));

		final var auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class)))
			.thenReturn(Mono.empty());

		final var patronRequestRepository = mock(PatronRequestRepository.class);
		when(patronRequestRepository.saveOrUpdate(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		final var context = new RequestWorkflowContext()
			.setPatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(PatronRequest.Status.LOANED)
				.build())
			.setPatronSystemCode(BORROWER)
			.setSupplierRequest(SupplierRequest.builder()
				.id(UUID.randomUUID())
				.localId("supplier-request-1")
				.localItemId("supplier-item-1")
				.localHoldCount(1)
				.hostLmsCode(SUPPLIER)
				.build());

		new HandleSupplierHoldDetected(patronRequestRepository, mock(SupplierRequestRepository.class),
			auditService, hostLmsService).attempt(context).block();

		final var audited = ArgumentCaptor.forClass(String.class);
		verify(auditService, times(2)).addAuditEntry(any(PatronRequest.class), audited.capture());
		assertThat(audited.getAllValues().get(0), containsString(SUPPLIER + " no longer has item supplier-item-1"));
		assertThat(audited.getAllValues().get(1), is("Borrowing Library told to prevent renewals"));
		verify(borrowerClient).preventRenewalOnLoan(any());
	}

	@Test
	void shouldAskTheSupplierAboutTheItemWithItsBibAndHolding() {
		final var supplierClient = mock(HostLmsClient.class);
		when(supplierClient.getItem(any())).thenReturn(Mono.just(HostLmsItem.builder()
			.localId("supplier-item-1")
			.holdCount(0)
			.build()));

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(supplierClient));

		final var auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class)))
			.thenReturn(Mono.empty());

		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		when(supplierRequestRepository.saveOrUpdate(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.localId("supplier-request-1")
			.localItemId("supplier-item-1")
			.localBibId("supplier-bib-1")
			.localHoldingId("supplier-holding-1")
			.localHoldCount(1)
			.hostLmsCode(SUPPLIER)
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(PatronRequest.builder()
				.id(UUID.randomUUID())
				.status(PatronRequest.Status.LOANED)
				.build())
			.setPatronSystemCode(BORROWER)
			.setSupplierRequest(supplierRequest);

		new HandleSupplierHoldDetected(mock(PatronRequestRepository.class), supplierRequestRepository,
			auditService, hostLmsService).attempt(context).block();

		// An Alma item's requests are read under its bib: without it the call went to bibs/null
		final var asked = ArgumentCaptor.forClass(HostLmsItem.class);
		verify(supplierClient).getItem(asked.capture());
		assertThat(asked.getValue().getBibId(), is("supplier-bib-1"));
		assertThat(asked.getValue().getHoldingId(), is("supplier-holding-1"));
		assertThat(supplierRequest.getLocalHoldCount(), is(0));
	}
}
