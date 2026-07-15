package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import reactor.core.publisher.Mono;

class NcipSupplierReturnExpectedNotifierTests {
	@Test
	void sendsCorrelatedItemShippedToNcipSupplier() {
		final var transport = mock(DeclarativeRequestTransport.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var addressResolver = mock(NcipAddressResolver.class);
		final var hostLms = new DataHostLms();
		hostLms.setCode("supplier-host");
		when(hostLmsService.findByCode("supplier-host"))
			.thenReturn(Mono.just(hostLms));
		when(addressResolver.agencyIdForHost(hostLms))
			.thenReturn("supplier-agency");
		when(addressResolver.systemIdForHost(hostLms))
			.thenReturn("supplier-system");
		when(addressResolver.agencyIdForLocalAgencyCode(
			"borrower-agency", "dcb-agency"))
			.thenReturn(Mono.just("borrower-agency"));
		when(transport.send(any(DeclarativeTransportRequest.class)))
			.thenReturn(Mono.just(new DeclarativeTransportResponse(
				"request-1:SUPPLIER", "CONFIRMED",
				NcipProtocol.ITEM_SHIPPED_RESPONSE, "response-1")));

		final var notifier = new NcipSupplierReturnExpectedNotifier(
			transport,
			new NcipPayloadBuilder(),
			hostLmsService,
			identityConfiguration(),
			addressResolver);
		final var context = context(NcipProtocol.PROTOCOL);

		assertThat(singleValueFrom(notifier.notifyExpectedReturn(context)),
			is(context));

		final var requestCaptor = ArgumentCaptor.forClass(
			DeclarativeTransportRequest.class);
		verify(transport).send(requestCaptor.capture());
		final var request = requestCaptor.getValue();
		assertThat(request.operation(), is(LifecycleOperation.REVISE_REQUEST));
		assertThat(request.messageKind(), is(NcipProtocol.ITEM_SHIPPED));
		assertThat(request.correlationId(), is("request-1:SUPPLIER"));
		assertThat(request.payload(), containsString(
			"<ItemIdentifierValue>item-1</ItemIdentifierValue>"));
	}

	@Test
	void ignoresNonNcipSupplier() {
		final var transport = mock(DeclarativeRequestTransport.class);
		final var notifier = new NcipSupplierReturnExpectedNotifier(
			transport,
			new NcipPayloadBuilder(),
			mock(HostLmsService.class),
			identityConfiguration(),
			mock(NcipAddressResolver.class));

		final var context = context(null);
		assertThat(singleValueFrom(notifier.notifyExpectedReturn(context)),
			is(context));
		verifyNoInteractions(transport);
	}

	private static RequestWorkflowContext context(String protocol) {
		final var patronRequest = PatronRequest.builder()
			.id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
			.patronRequest(patronRequest)
			.hostLmsCode("supplier-host")
			.localAgency("supplier-agency")
			.localItemId("item-local-1")
			.localItemBarcode("item-1")
			.localId("request-1:SUPPLIER")
			.protocol(protocol)
			.build();
		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest)
			.setPatronAgencyCode("borrower-agency");
	}

	private static NcipIdentityConfiguration identityConfiguration() {
		final var configuration = new NcipIdentityConfiguration();
		configuration.setSystemId("dcb-system");
		configuration.setAgencyId("dcb-agency");
		return configuration;
	}
}
