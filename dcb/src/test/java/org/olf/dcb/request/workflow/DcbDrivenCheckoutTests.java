package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

/**
 * The two paths where DCB performs the checkout itself rather than watching staff do it:
 * pickup anywhere, and walk-up. An ILS asked to check out needs to be told which item and
 * which patron, and an ILS that finds items by barcode cannot act without one.
 */
@TestInstance(PER_CLASS)
class DcbDrivenCheckoutTests {
	private static final String BORROWER = "BORROWING-SYSTEM";
	private static final String SUPPLIER = "SUPPLYING-SYSTEM";

	private HostLmsClient borrowerClient;
	private HostLmsClient supplierClient;
	private HostLmsService hostLmsService;
	private PatronRequestAuditService auditService;
	private PatronRequestRepository patronRequestRepository;

	@BeforeEach
	void beforeEach() {
		borrowerClient = mock(HostLmsClient.class);
		supplierClient = mock(HostLmsClient.class);

		hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrowerClient));
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(supplierClient));

		auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class)))
			.thenReturn(Mono.empty());
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class), any()))
			.thenReturn(Mono.empty());

		patronRequestRepository = mock(PatronRequestRepository.class);
		when(patronRequestRepository.saveOrUpdate(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void pickupAnywhereCheckoutNamesTheItemItsBarcodeAndTheBorrowersOwnPatron() {
		when(borrowerClient.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));

		final var transition = new HandleBorrowerItemLoaned(patronRequestRepository,
			hostLmsService, auditService);

		transition.checkHomeItemOutToLocalPatron(context()).block();

		final var command = commandSentTo(borrowerClient);

		assertThat(command.getItemId(), is("virtual-item-1"));
		// Alma reads the item by barcode to find its library; without this the checkout cannot start
		assertThat(command.getItemBarcode(), is("supplier-item-barcode"));
		assertThat(command.getPatronId(), is("home-patron-1"));
		assertThat(command.getPatronBarcode(), is("home-barcode"));
	}

	@Test
	void pickupAnywhereMarksTheRequestForAttentionWhenTheCheckoutIsRefused() {
		when(borrowerClient.checkOutItemToPatron(any()))
			.thenReturn(Mono.error(new IllegalArgumentException("Item barcode is required")));

		final var transition = new HandleBorrowerItemLoaned(patronRequestRepository,
			hostLmsService, auditService);

		final var context = context();

		transition.checkHomeItemOutToLocalPatron(context).block();

		// The physical loan happened at the pickup library, so the request stands - but the
		// patron's own library has no record of it and someone has to put that right
		assertThat(context.getPatronRequest().getNeedsAttention(), is(true));
	}

	@Test
	void walkUpCheckoutNamesTheItemItsBarcodeAndTheBorrowersOwnPatron() {
		when(borrowerClient.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));
		when(supplierClient.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));
		when(supplierClient.reflectPatronLoanAtSupplier()).thenReturn(true);

		final var transition = new ExpeditedCheckoutTransition(patronRequestRepository,
			auditService, hostLmsService);

		final var context = context();

		transition.attempt(context).block();

		final var command = commandSentTo(borrowerClient);

		assertThat(command.getItemId(), is("virtual-item-1"));
		assertThat(command.getItemBarcode(), is("supplier-item-barcode"));
		// The virtual identity belongs to the supplier; the borrower knows this patron by their own id
		assertThat(command.getPatronId(), is("home-patron-1"));
		assertThat(context.getPatronRequest().getStatus(), is(PatronRequest.Status.LOANED));
	}

	@Test
	void walkUpDoesNotRecordALoanTheSupplierRefused() {
		when(borrowerClient.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));
		when(supplierClient.reflectPatronLoanAtSupplier()).thenReturn(true);
		when(supplierClient.checkOutItemToPatron(any()))
			.thenReturn(Mono.error(new IllegalStateException("Alma refused the loan")));

		final var transition = new ExpeditedCheckoutTransition(patronRequestRepository,
			auditService, hostLmsService);

		final var context = context();

		assertThrows(IllegalStateException.class, () -> transition.attempt(context).block());

		// The supplier checkout is the loan: the patron is at that desk with the item
		assertThat(context.getPatronRequest().getStatus(), is(not(PatronRequest.Status.LOANED)));
	}

	private static CheckoutItemCommand commandSentTo(HostLmsClient client) {
		final var captor = ArgumentCaptor.forClass(CheckoutItemCommand.class);

		verify(client).checkOutItemToPatron(captor.capture());

		return captor.getValue();
	}

	private RequestWorkflowContext context() {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.patronHostlmsCode(BORROWER)
			.localRequestId("borrower-request-1")
			.localItemId("virtual-item-1")
			.status(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
			.build();

		final var homeIdentity = PatronIdentity.builder()
			.id(UUID.randomUUID())
			.localId("home-patron-1")
			.localBarcode("[home-barcode]")
			.localHomeLibraryCode("HOME-BRANCH")
			.build();

		final var virtualIdentity = PatronIdentity.builder()
			.id(UUID.randomUUID())
			.localId("virtual-patron-1")
			.localBarcode("[home-barcode]")
			.build();

		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.localId("supplier-request-1")
			.localItemId("supplier-item-1")
			.localItemBarcode("supplier-item-barcode")
			.hostLmsCode(SUPPLIER)
			.build();

		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setPatronSystemCode(BORROWER)
			.setPatronHomeIdentity(homeIdentity)
			.setPatronVirtualIdentity(virtualIdentity)
			.setSupplierRequest(supplierRequest)
			.setLenderSystemCode(SUPPLIER);
	}
}
