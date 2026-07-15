package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import io.micronaut.context.BeanProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

class HandleSupplierItemAvailableTests {
	@Test
	void completesWhenBorrowerUpdateCompletesWithoutValue() {
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var mockSupplierRequestRepository = mock(SupplierRequestRepository.class);
		final var auditService = mock(PatronRequestAuditService.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var hostLmsClient = mock(HostLmsClient.class);
		final var patronRequest = PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(PatronRequest.Status.RETURN_TRANSIT)
			.patronHostlmsCode("borrower-host")
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
		when(mockSupplierRequestRepository.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString()))
			.thenReturn(Mono.just(PatronRequestAudit.builder().build()));

		final var transition = new HandleSupplierItemAvailable(
			patronRequestRepository,
			mockSupplierRequestRepository,
			mock(BeanProvider.class),
			auditService,
			mock(RequestWorkflowContextHelper.class),
			hostLmsService);

		assertThat(singleValueFrom(transition.attempt(context)), is(context));
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.COMPLETED));
	}
}
