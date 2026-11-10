package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.zalando.problem.ThrowableProblem;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The outcomes of retrying a lender checkout, with the lender's system mocked. Who may retry, and
 * when, is covered through the API by RetryLenderCheckoutApiTests.
 */
class RetryLenderCheckoutServiceTests {
	private static final String LENDER = "LENDER";

	private HostLmsClient lender;
	private PatronRequestAuditService auditService;
	private PatronRequestAuditRepository auditRepository;
	private PatronRequestRepository patronRequestRepository;
	private RetryLenderCheckoutService service;
	private PatronRequest patronRequest;

	@BeforeEach
	void beforeEach() {
		patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.LOANED)
			.needsAttention(true)
			.build();

		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setLenderSystemCode(LENDER)
			.setPatronVirtualIdentity(PatronIdentity.builder().localId("virtual-1").localBarcode("[vp-barcode]").build())
			.setSupplierRequest(SupplierRequest.builder()
				.id(UUID.randomUUID())
				.localId("hold-1")
				.localItemId("item-1")
				.localAgency("LENDING_AG")
				.build());

		final var patronRequestService = mock(PatronRequestService.class);
		when(patronRequestService.findById(patronRequest.getId())).thenReturn(Mono.just(patronRequest));

		final var contextHelper = mock(RequestWorkflowContextHelper.class);
		when(contextHelper.fromPatronRequest(patronRequest)).thenReturn(Mono.just(context));

		lender = mock(HostLmsClient.class);
		when(lender.reflectPatronLoanAtSupplier()).thenReturn(true);

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(LENDER)).thenReturn(Mono.just(lender));

		auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class), anyMap()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));

		auditRepository = mock(PatronRequestAuditRepository.class);
		when(auditRepository.findAllByPatronRequest(patronRequest)).thenReturn(Flux.empty());

		patronRequestRepository = mock(PatronRequestRepository.class);
		when(patronRequestRepository.saveOrUpdate(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));

		final var handleBorrowerItemLoaned = new HandleBorrowerItemLoaned(patronRequestRepository,
			hostLmsService, auditService);

		service = new RetryLenderCheckoutService(patronRequestService, contextHelper, hostLmsService,
			handleBorrowerItemLoaned, auditService, auditRepository, patronRequestRepository,
			new LenderCheckoutRetryLimiter(100));
	}

	@Test
	void aRetryThatSucceedsChecksTheCopyOutAndClearsTheFlag() {
		lenderCopyIs("TRANSIT");
		when(lender.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));

		final var result = service.retry(patronRequest.getId(), consortium(), "consortium-staff").block();

		assertThat(result, is(RetryLenderCheckoutService.RETRIED));
		assertThat(patronRequest.getNeedsAttention(), is(false));
		verify(lender).checkOutItemToPatron(any());
	}

	@Test
	void aCopyAlreadyCheckedOutIsNotCheckedOutAgain() {
		// A second checkout to the same virtual patron would renew the loan
		lenderCopyIs(HostLmsItem.ITEM_LOANED);

		final var result = service.retry(patronRequest.getId(), consortium(), "consortium-staff").block();

		assertThat(result, is(RetryLenderCheckoutService.ALREADY_OUT));
		verify(lender, never()).checkOutItemToPatron(any());
	}

	@Test
	void aRefusedRetryIsAuditedAndKeepsTheFlag() {
		lenderCopyIs("TRANSIT");
		when(lender.checkOutItemToPatron(any())).thenReturn(Mono.error(new IllegalStateException("Patron block")));

		assertThrows(ThrowableProblem.class, () -> service.retry(patronRequest.getId(), consortium(), "consortium-staff").block());

		assertThat(patronRequest.getNeedsAttention(), is(true));
		verify(auditService).addAuditEntry(eq(patronRequest), eq(RetryLenderCheckoutService.RETRY_FAILED), anyMap());
	}

	@Test
	void theFlagStaysWhenThePatronsOwnLibraryCheckoutAlsoFailed() {
		lenderCopyIs("TRANSIT");
		when(lender.checkOutItemToPatron(any())).thenReturn(Mono.just("OK"));
		when(auditRepository.findAllByPatronRequest(patronRequest)).thenReturn(Flux.just(
			PatronRequestAudit.builder().briefDescription("Patron checkout failed : refused").build()));

		service.retry(patronRequest.getId(), consortium(), "consortium-staff").block();

		assertThat(patronRequest.getNeedsAttention(), is(true));
	}

	private void lenderCopyIs(String status) {
		when(lender.getItem(any())).thenReturn(Mono.just(HostLmsItem.builder().localId("item-1").status(status).build()));
	}

	private static CallerScope consortium() {
		return CallerScope.from(List.of("CONSORTIUM_ADMIN"), Map.of());
	}
}
