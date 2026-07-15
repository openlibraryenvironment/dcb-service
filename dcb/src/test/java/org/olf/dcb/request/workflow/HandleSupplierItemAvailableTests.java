package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.context.BeanProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HandleSupplierItemAvailableTests {
	@Test
	void completesWhenBorrowerUpdateCompletesWithoutValue() {
		final var fixture = fixture();

		StepVerifier.create(fixture.transition().attempt(fixture.context()))
			.expectNext(fixture.context())
			.verifyComplete();

		assertThat(fixture.patronRequest().getStatus(),
			is(PatronRequest.Status.COMPLETED));
		final var itemCaptor = ArgumentCaptor.forClass(HostLmsItem.class);
		verify(fixture.hostLmsClient()).updateItemStatus(
			itemCaptor.capture(),
			any(HostLmsClient.CanonicalItemState.class));
		assertThat(itemCaptor.getValue().getLocalId(), is("borrower-item"));
		assertThat(itemCaptor.getValue().getLocalRequestId(), is("borrower-request"));
		verify(fixture.hostLmsClient()).updateItemStatus(
			any(HostLmsItem.class),
			org.mockito.ArgumentMatchers.eq(HostLmsClient.CanonicalItemState.COMPLETED));
		verify(fixture.supplierRequestRepository())
			.saveOrUpdate(fixture.supplierRequest());
		verify(fixture.patronRequestRepository())
			.saveOrUpdate(fixture.patronRequest());
		verify(fixture.auditService()).addAuditEntry(
			fixture.patronRequest(),
			"Supplier Item Available - Infers item back on the shelf after loan. Completing request");
	}

	@Test
	void borrowerUpdateFailureDoesNotPersistCompletion() {
		final var fixture = fixture();
		final var failure = new IllegalStateException("borrower unavailable");
		when(fixture.hostLmsClient().updateItemStatus(
			any(HostLmsItem.class),
			any(HostLmsClient.CanonicalItemState.class)))
			.thenReturn(Mono.error(failure));

		StepVerifier.create(fixture.transition().attempt(fixture.context()))
			.expectErrorMatches(error -> error == failure)
			.verify();

		assertThat(fixture.patronRequest().getStatus(),
			is(PatronRequest.Status.RETURN_TRANSIT));
		verify(fixture.supplierRequestRepository(), never()).saveOrUpdate(any());
		verify(fixture.patronRequestRepository(), never()).saveOrUpdate(any());
		verify(fixture.auditService(), never()).addAuditEntry(any(), anyString());
	}

	@Test
	void appliesOnlyToReturnedRequestsWithSupplierReceiptEvidence() {
		final var fixture = fixture();

		assertThat(fixture.transition().isApplicableFor(fixture.context()), is(true));
		fixture.supplierRequest().setLocalItemStatus(null).setLocalStatus("CLOSED");
		assertThat(fixture.transition().isApplicableFor(fixture.context()), is(true));
		fixture.supplierRequest().setLocalStatus("CONFIRMED");
		assertThat(fixture.transition().isApplicableFor(fixture.context()), is(false));
		fixture.supplierRequest().setLocalItemStatus(HostLmsItem.ITEM_RECEIVED);
		assertThat(fixture.transition().isApplicableFor(fixture.context()), is(true));
		fixture.patronRequest().setStatus(PatronRequest.Status.COMPLETED);
		assertThat(fixture.transition().isApplicableFor(fixture.context()), is(false));
	}

	private static Fixture fixture() {
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var auditService = mock(PatronRequestAuditService.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var hostLmsClient = mock(HostLmsClient.class);
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.RETURN_TRANSIT)
			.patronHostlmsCode("borrower-host")
			.localRequestId("borrower-request")
			.localItemId("borrower-item")
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("supplier-host")
			.localItemId("item-1")
			.localItemStatus(HostLmsItem.ITEM_AVAILABLE)
			.build();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(hostLmsService.getClientFor("borrower-host"))
			.thenReturn(Mono.just(hostLmsClient));
		when(hostLmsClient.updateItemStatus(
			any(HostLmsItem.class),
			any(HostLmsClient.CanonicalItemState.class)))
			.thenReturn(Mono.empty());
		when(patronRequestRepository.saveOrUpdate(patronRequest))
			.thenReturn(Mono.just(patronRequest));
		when(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));

		final var transition = new HandleSupplierItemAvailable(
			patronRequestRepository,
			supplierRequestRepository,
			mock(BeanProvider.class),
			auditService,
			mock(RequestWorkflowContextHelper.class),
			hostLmsService);

		return new Fixture(
			patronRequestRepository,
			supplierRequestRepository,
			auditService,
			hostLmsClient,
			patronRequest,
			supplierRequest,
			context,
			transition);
	}

	private record Fixture(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService auditService,
		HostLmsClient hostLmsClient,
		PatronRequest patronRequest,
		SupplierRequest supplierRequest,
		RequestWorkflowContext context,
		HandleSupplierItemAvailable transition) {
	}
}
