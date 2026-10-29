package org.olf.dcb.request.fulfilment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.zalando.problem.Problem;

import reactor.core.publisher.Mono;

/**
 * Deleting the supplier's hold when a request finishes. The lender's system has usually removed a
 * filled hold already, so "not found" is the expected answer, not a failure.
 */
class SupplierHoldDeletionTests {
	private static final String SUPPLIER = "SUPPLIER";

	private HostLmsClient client;
	private PatronRequestAuditService auditService;
	private SupplyingAgencyService service;

	@BeforeEach
	void beforeEach() {
		client = mock(HostLmsClient.class);
		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(client));
		when(client.getRequest(any())).thenReturn(Mono.just(HostLmsRequest.builder().localId("hold-1").build()));

		auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class), anyMap()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));

		service = new SupplyingAgencyService(hostLmsService, null, null, null, null, null,
			auditService, null);
	}

	@Test
	void aHoldTheLenderNoLongerHasIsRecordedAsAlreadyDeleted() {
		when(client.deleteHold(any())).thenReturn(Mono.error(Problem.builder()
			.withTitle("Unexpected response from: DELETE /iii/sierra-api/v6/patrons/holds/hold-1")
			.with("responseStatusCode", 404)
			.build()));

		final var context = context();

		singleValueFrom(service.cleanUp(context));

		verify(auditService).addAuditEntry(eq(context.getPatronRequest()),
			eq("Delete supplier hold : Already deleted"), anyMap());
	}

	@Test
	void aCancellationWithNoVirtualIdentityStillDeletesTheHold() {
		when(client.deleteHold(any())).thenReturn(Mono.just("OK_DELETED"));

		final var context = context();

		singleValueFrom(service.cleanUp(context));

		verify(client).deleteHold(any(DeleteCommand.class));
		verify(auditService).addAuditEntry(eq(context.getPatronRequest()),
			eq("Delete supplier hold request : Success (Deleted)"), anyMap());
	}

	@Test
	void aDeleteTheLenderRefusesIsRecordedAsFailed() {
		when(client.deleteHold(any())).thenReturn(Mono.just("ERROR"));

		final var context = context()
			.setPatronVirtualIdentity(PatronIdentity.builder().localId("virtual-patron-1").build());

		singleValueFrom(service.cleanUp(context));

		verify(auditService).addAuditEntry(eq(context.getPatronRequest()),
			eq("Delete supplier hold : Failed"), anyMap());
	}

	private static RequestWorkflowContext context() {
		final var patronRequest = PatronRequest.builder().id(UUID.randomUUID()).build();

		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(SupplierRequest.builder()
				.id(UUID.randomUUID())
				.hostLmsCode(SUPPLIER)
				.localId("hold-1")
				.build());
	}
}
