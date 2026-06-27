package org.olf.dcb.request.lifecycle.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

class InboundLifecycleMessageHandlerTests {
	@Test
	void supplierMessageProjectsSupplierEvidenceAndProgressesWorkflow() {
		final var patronRequestId = UUID.randomUUID();
		final var patronRequest = new PatronRequest().setId(patronRequestId);
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
		final var dependencies = dependenciesFor(patronRequest, context);
		final var handler = dependencies.handler();
		when(dependencies.supplierRequestRepository()
			.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));

		final var result = handler.handle(new InboundLifecycleMessage(
			"ncip-v202",
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			patronRequestId + ":SUPPLIER",
			"CONFIRMED",
			"confirmed",
			"supplier-item",
			"supplier-barcode",
			Instant.parse("2026-06-26T12:00:00Z"),
			"message-1")).block();

		assertThat(result, is(context));
		assertThat(supplierRequest.getLocalId(), is("supplier-remote-request"));
		assertThat(supplierRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(supplierRequest.getRawLocalStatus(), is("confirmed"));
		assertThat(supplierRequest.getLocalItemId(), is("supplier-item"));
		assertThat(supplierRequest.getLocalItemBarcode(), is("supplier-barcode"));
		assertThat(supplierRequest.getProtocol(), is("ncip-v202"));
		verify(dependencies.patronRequestWorkflowService()).progressUsing(context);
	}

	@Test
	void borrowerMessageProjectsPatronRequestEvidenceWithoutRequiringArtifacts() {
		final var patronRequestId = UUID.randomUUID();
		final var patronRequest = new PatronRequest().setId(patronRequestId);
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var dependencies = dependenciesFor(patronRequest, context);
		final var handler = dependencies.handler();
		when(dependencies.patronRequestRepository()
			.saveOrUpdate(patronRequest))
			.thenReturn(Mono.just(patronRequest));

		handler.handle(new InboundLifecycleMessage(
			"ncip-v202",
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			patronRequestId + ":BORROWER",
			"PLACED",
			"placed",
			null,
			null,
			Instant.parse("2026-06-26T12:01:00Z"),
			"message-2")).block();

		assertThat(patronRequest.getLocalRequestId(),
			is("borrower-remote-request"));
		assertThat(patronRequest.getLocalRequestStatus(), is("PLACED"));
		assertThat(patronRequest.getRawLocalRequestStatus(), is("placed"));
		assertThat(patronRequest.getProtocol(), is("ncip-v202"));
		assertThat(patronRequest.getLocalItemId(), nullValue());
		verify(dependencies.patronRequestWorkflowService()).progressUsing(context);
	}

	@Test
	void duplicateMessageIsIgnoredByIdempotencyGuard() {
		final var patronRequestId = UUID.randomUUID();
		final var patronRequest = new PatronRequest().setId(patronRequestId);
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
		final var dependencies = dependenciesFor(patronRequest, context);
		final var handler = dependencies.handler();
		when(dependencies.supplierRequestRepository()
			.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));
		final var message = new InboundLifecycleMessage(
			"ncip-v202",
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			patronRequestId + ":SUPPLIER",
			"CONFIRMED",
			"confirmed",
			null,
			null,
			Instant.parse("2026-06-26T12:02:00Z"),
			"message-3");

		handler.handle(message).block();
		handler.handle(message).blockOptional();

		verify(dependencies.patronRequestWorkflowService(), times(1))
			.progressUsing(context);
	}

	private static HandlerDependencies dependenciesFor(
		PatronRequest patronRequest,
		RequestWorkflowContext context) {

		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var requestWorkflowContextHelper = mock(RequestWorkflowContextHelper.class);
		final var patronRequestWorkflowService = mock(PatronRequestWorkflowService.class);
		final var patronRequestAuditService = mock(PatronRequestAuditService.class);
		final var idempotencyGuard = new InboundLifecycleMessageIdempotencyGuard();

		when(patronRequestRepository.findById(patronRequest.getId()))
			.thenReturn(Mono.just(patronRequest));
		when(requestWorkflowContextHelper.fromPatronRequest(patronRequest))
			.thenReturn(Mono.just(context));
		when(patronRequestAuditService.addAuditEntry(
			org.mockito.ArgumentMatchers.eq(patronRequest),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyMap()))
			.thenReturn(Mono.empty());
		when(patronRequestWorkflowService.progressUsing(context))
			.thenReturn(Mono.just(context));

		return new HandlerDependencies(
			patronRequestRepository,
			supplierRequestRepository,
			patronRequestWorkflowService,
			new InboundLifecycleMessageHandler(
				patronRequestRepository,
				supplierRequestRepository,
				requestWorkflowContextHelper,
				patronRequestWorkflowService,
				patronRequestAuditService,
				idempotencyGuard));
	}

	private record HandlerDependencies(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestWorkflowService patronRequestWorkflowService,
		InboundLifecycleMessageHandler handler) {
	}
}
