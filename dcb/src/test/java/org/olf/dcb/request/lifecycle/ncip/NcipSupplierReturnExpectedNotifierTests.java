package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.interaction.ncip.NcipSchemaPath;
import org.olf.dcb.core.interaction.ncip.NcipSchemaValidator;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class NcipSupplierReturnExpectedNotifierTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(
		NcipSchemaPath.schemaPath());

	@Test
	void sendsValidCorrelatedItemShippedToNcipSupplier() {
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
		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.SUPPLIER));
		assertThat(request.operation(), is(LifecycleOperation.REVISE_REQUEST));
		assertThat(request.hostLmsCode(), is("supplier-host"));
		assertThat(request.agencyCode(), is("supplier-agency"));
		assertThat(request.messageKind(), is(NcipProtocol.ITEM_SHIPPED));
		assertThat(request.correlationId(), is("request-1:SUPPLIER"));
		assertDoesNotThrow(() -> validator.validate(request.payload()));

		final var document = parse(request.payload());
		assertThat(payloadName(document), is(NcipProtocol.ITEM_SHIPPED));
		assertThat(text(document, "FromSystemId"), is("dcb-system"));
		assertThat(agency(document, "FromAgencyId"), is("borrower-agency"));
		assertThat(text(document, "ToSystemId"), is("supplier-system"));
		assertThat(agency(document, "ToAgencyId"), is("supplier-agency"));
		assertThat(text(document, "RequestIdentifierValue"),
			is("request-1:SUPPLIER"));
		assertThat(text(document, "ItemIdentifierValue"), is("item-1"));
	}

	@Test
	void fallsBackToSupplierRoleCorrelationFromPatronRequest() {
		final var transport = successfulTransport();
		final var context = context(NcipProtocol.PROTOCOL);
		context.getSupplierRequest().setLocalId(null);

		assertThat(singleValueFrom(notifier(transport).notifyExpectedReturn(context)),
			is(context));

		final var requestCaptor = ArgumentCaptor.forClass(
			DeclarativeTransportRequest.class);
		verify(transport).send(requestCaptor.capture());
		assertThat(requestCaptor.getValue().correlationId(),
			is("11111111-1111-1111-1111-111111111111:SUPPLIER"));
	}

	@Test
	void rejectsMissingRequiredRoutingAndCorrelationData() {
		final var transport = mock(DeclarativeRequestTransport.class);
		final var missingHost = context(NcipProtocol.PROTOCOL);
		missingHost.getSupplierRequest().setHostLmsCode(" ");
		assertThrows(IllegalArgumentException.class,
			() -> notifier(transport).notifyExpectedReturn(missingHost));

		final var missingItem = context(NcipProtocol.PROTOCOL);
		missingItem.getSupplierRequest().setLocalItemBarcode(null);
		assertThrows(IllegalArgumentException.class,
			() -> notifier(transport).notifyExpectedReturn(missingItem));

		final var missingCorrelation = context(NcipProtocol.PROTOCOL);
		missingCorrelation.getSupplierRequest().setLocalId(null);
		missingCorrelation.setPatronRequest(null);
		assertThrows(IllegalArgumentException.class,
			() -> notifier(transport).notifyExpectedReturn(missingCorrelation));
		verifyNoInteractions(transport);
	}

	@Test
	void propagatesTransportFailure() {
		final var transport = mock(DeclarativeRequestTransport.class);
		final var failure = new IllegalStateException("supplier unavailable");
		when(transport.send(any())).thenReturn(Mono.error(failure));

		StepVerifier.create(notifier(transport)
			.notifyExpectedReturn(context(NcipProtocol.PROTOCOL)))
			.expectErrorMatches(error -> error == failure)
			.verify();
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

	private static NcipSupplierReturnExpectedNotifier notifier(
		DeclarativeRequestTransport transport) {

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

		return new NcipSupplierReturnExpectedNotifier(
			transport,
			new NcipPayloadBuilder(),
			hostLmsService,
			identityConfiguration(),
			addressResolver);
	}

	private static DeclarativeRequestTransport successfulTransport() {
		final var transport = mock(DeclarativeRequestTransport.class);
		when(transport.send(any())).thenReturn(Mono.just(
			new DeclarativeTransportResponse(
				"request-1:SUPPLIER", "CONFIRMED",
				NcipProtocol.ITEM_SHIPPED_RESPONSE, "response-1")));
		return transport;
	}

	private static Document parse(String xml) {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(
				xml.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Could not parse NCIP XML", e);
		}
	}

	private static String payloadName(Document document) {
		return ((Element) document.getDocumentElement()
			.getElementsByTagNameNS(NcipPayloadBuilder.NCIP_NAMESPACE, "*")
			.item(0)).getLocalName();
	}

	private static String text(Document document, String localName) {
		return document.getElementsByTagNameNS(
			NcipPayloadBuilder.NCIP_NAMESPACE, localName)
			.item(0).getTextContent();
	}

	private static String agency(Document document, String wrapperName) {
		final var wrapper = (Element) document.getElementsByTagNameNS(
			NcipPayloadBuilder.NCIP_NAMESPACE, wrapperName).item(0);
		return wrapper.getElementsByTagNameNS(
			NcipPayloadBuilder.NCIP_NAMESPACE, "AgencyId")
			.item(0).getTextContent();
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
