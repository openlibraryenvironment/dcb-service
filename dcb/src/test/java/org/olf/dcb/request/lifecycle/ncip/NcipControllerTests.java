package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.k_int.peerauth.service.PeerJwksResolver;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.interaction.ncip.NcipSchemaPath;
import org.olf.dcb.core.interaction.ncip.NcipSchemaValidator;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import reactor.core.publisher.Mono;
import java.util.Map;

class NcipControllerTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(
		NcipSchemaPath.schemaPath());

	@Test
	void acceptsItemShippedAndReturnsItemShippedResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemShipped()).block();

		assertThat(response.getContentType().orElseThrow(),
			is(MediaType.APPLICATION_XML_TYPE));
		assertThat(response.body(), containsString("<ItemShippedResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_TRANSIT));
		assertThat(message.rawStatus(), is("ItemShipped"));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void returnsProblemForInvalidXml() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		final var controller = controllerWith(handler);

		final var response = controller.receive("<not-ncip/>").block();

		assertThat(response.body(), containsString("<Problem"));
		assertThat(response.body(), containsString("<ProblemDetail>"));
		assertDoesNotThrow(() -> validator.validate(response.body()));
	}

	@Test
	void treatsDuplicateOrAlreadySeenMessagesAsSuccess() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.empty());
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemShipped()).block();

		assertThat(response.body(), containsString("<ItemShippedResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));
	}

	@Test
	void acceptsRequestItemResponseAndReturnsNoContent() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validRequestItemResponse()).block();

		assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.REQUEST_ITEM_RESPONSE));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void acceptsItemRequestedAndReturnsItemRequestedResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemRequested()).block();

		assertThat(response.getContentType().orElseThrow(),
			is(MediaType.APPLICATION_XML_TYPE));
		assertThat(response.body(), containsString("<ItemRequestedResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_REQUESTED));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void acceptsCancelRequestItemAndReturnsCancelRequestItemResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validCancelRequestItem()).block();

		assertThat(response.getContentType().orElseThrow(),
			is(MediaType.APPLICATION_XML_TYPE));
		assertThat(response.body(), containsString("<CancelRequestItemResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("MISSING"));
		assertThat(message.rawStatus(), is(NcipProtocol.CANCEL_REQUEST_ITEM + ":NOT_ON_SHELF"));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void acceptsAcceptItemResponseAndReturnsNoContent() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validAcceptItemResponse()).block();

		assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(message.hostLmsCode(), is("borrower-host"));
		assertThat(message.hostRequestId(), is("request-1:BORROWER"));
		assertThat(message.correlationId(), is("request-1:BORROWER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void acceptsItemReceivedAndReturnsItemReceivedResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemReceived()).block();

		assertThat(response.body(), containsString("<ItemReceivedResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("borrower-host"));
		assertThat(message.hostRequestId(), is("request-1:BORROWER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_RECEIVED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_RECEIVED));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.protocolProperties().get("fromAgencyId"), is("borrower-host"));
		assertThat(message.protocolProperties().get("toAgencyId"), is("dcb-host"));
	}

	@Test
	void acceptsItemCheckedInAndReturnsItemCheckedInResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemCheckedIn()).block();

		assertThat(response.body(), containsString("<ItemCheckedInResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("borrower-host"));
		assertThat(message.hostRequestId(), is("request-1:BORROWER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_ON_HOLDSHELF));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_IN));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.protocolProperties().get("fromAgencyId"), is("borrower-host"));
		assertThat(message.protocolProperties().get("toAgencyId"), is("dcb-host"));
	}

	@Test
	void acceptsSupplierItemCheckedInAsReceivedEvidence() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validSupplierItemCheckedIn()).block();

		assertThat(response.body(), containsString("<ItemCheckedInResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_RECEIVED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_IN));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.protocolProperties().get("fromAgencyId"), is("supplier-host"));
		assertThat(message.protocolProperties().get("toAgencyId"), is("dcb-host"));
	}

	@Test
	void acceptsItemCheckedOutAndReturnsItemCheckedOutResponse() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		final var controller = controllerWith(handler);

		final var response = controller.receive(validItemCheckedOut()).block();

		assertThat(response.body(), containsString("<ItemCheckedOutResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));

		final var messageCaptor = ArgumentCaptor.forClass(
			InboundLifecycleMessage.class);
		verify(handler).handle(messageCaptor.capture());

		final var message = messageCaptor.getValue();
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("borrower-host"));
		assertThat(message.hostRequestId(), is("request-1:BORROWER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_LOANED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_OUT));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.protocolProperties().get("fromAgencyId"), is("borrower-host"));
		assertThat(message.protocolProperties().get("toAgencyId"), is("dcb-host"));
	}

	@Test
	void rejectsPeerAuthEnabledRequestWithoutBearerToken() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		final var properties = new DcbPeerAuthProperties();
		properties.setEnabled(true);
		final var ncip = new DcbPeerAuthProperties.Ncip();
		ncip.setEnabled(true);
		properties.setNcip(ncip);
		final var controller = new NcipController(
			handler,
			new NcipInboundXmlMapper(),
			new NcipResponseBuilder(),
			new NcipSchemaValidator(NcipSchemaPath.schemaPath()),
			new NcipPeerAuthGuard(
				properties,
				hostLmsResolverWithJwtRequiredPeer(),
				mock(PeerJwksResolver.class),
				new NcipResponseBuilder()));

		final var response = controller.receive(
			HttpRequest.POST("/ncip/v2_02", validItemShipped()),
			validItemShipped()).block();

		assertThat(response.getStatus(), is(HttpStatus.UNAUTHORIZED));
		assertThat(response.body(), containsString("Missing peer bearer token"));
	}

	private static NcipController controllerWith(
		InboundLifecycleMessageHandler handler) {

		return new NcipController(
			handler,
			new NcipInboundXmlMapper(),
			new NcipResponseBuilder(),
			new NcipSchemaValidator(NcipSchemaPath.schemaPath()),
			disabledPeerAuthGuard());
	}

	private static NcipPeerAuthGuard disabledPeerAuthGuard() {
		final var resolver = mock(NcipPeerHostLmsResolver.class);
		when(resolver.findBySystemId(any())).thenAnswer(invocation -> Mono.just(DataHostLms.builder()
			.code("test-host")
			.clientConfig(Map.of(
				"ncip-system-id", invocation.getArgument(0),
				"ncip-peer-auth-mode", "INSECURE"))
			.build()));
		return new NcipPeerAuthGuard(
			new DcbPeerAuthProperties(),
			resolver,
			mock(PeerJwksResolver.class),
			new NcipResponseBuilder());
	}

	private static NcipPeerHostLmsResolver hostLmsResolverWithJwtRequiredPeer() {
		final var resolver = mock(NcipPeerHostLmsResolver.class);
		when(resolver.findBySystemId(any())).thenAnswer(invocation -> Mono.just(DataHostLms.builder()
			.code(invocation.getArgument(0))
			.clientConfig(Map.of(
				"ncip-system-id", invocation.getArgument(0),
				"ncip-peer-auth-mode", "JWT_REQUIRED",
				"ncip-peer-issuer", "https://ors.example",
				"ncip-peer-jwks-url", "https://ors.example/jwks",
				"ncip-peer-audience", "ors"))
			.build()));
		return resolver;
	}

	static String validItemShipped() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemShipped>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <RequestId>
			      <RequestIdentifierValue>request-1:SUPPLIER</RequestIdentifierValue>
			    </RequestId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <DateShipped>2026-06-26T12:03:00Z</DateShipped>
			    <ShippingInformation>
			      <ElectronicAddress>
			        <ElectronicAddressType>Email</ElectronicAddressType>
			        <ElectronicAddressData>supplier@example.org</ElectronicAddressData>
			      </ElectronicAddress>
			    </ShippingInformation>
			  </ItemShipped>
			</NCIPMessage>
			""";
	}

	static String validBorrowerItemShipped() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemShipped>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>borrower-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <RequestId>
			      <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
			    </RequestId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <DateShipped>2026-06-26T12:07:00Z</DateShipped>
			    <ShippingInformation>
			      <ElectronicAddress>
			        <ElectronicAddressType>Email</ElectronicAddressType>
			        <ElectronicAddressData>borrower@example.org</ElectronicAddressData>
			      </ElectronicAddress>
			    </ShippingInformation>
			  </ItemShipped>
			</NCIPMessage>
			""";
	}

	static String validRequestItemResponse() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <RequestItemResponse>
			    <ResponseHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </ResponseHeader>
			    <RequestId>
			      <RequestIdentifierValue>request-1:SUPPLIER</RequestIdentifierValue>
			    </RequestId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <UserId>
			      <AgencyId>borrower-host</AgencyId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <RequestType>Hold</RequestType>
			    <RequestScopeType>Bibliographic Item</RequestScopeType>
			  </RequestItemResponse>
			</NCIPMessage>
			""";
	}

	static String validItemReceived() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemReceived>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>borrower-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <UserId>
			      <AgencyId>borrower-host</AgencyId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <RequestId>
			      <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
			    </RequestId>
			    <DateReceived>2026-06-26T12:05:00Z</DateReceived>
			  </ItemReceived>
			</NCIPMessage>
			""";
	}

	static String validItemCheckedIn() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemCheckedIn>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>borrower-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <UserId>
			      <AgencyId>borrower-host</AgencyId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <Ext>
			      <RequestId>
			        <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
			      </RequestId>
			    </Ext>
			  </ItemCheckedIn>
			</NCIPMessage>
			""";
	}

	static String validSupplierItemCheckedIn() {
		return validItemCheckedIn()
			.replace("borrower-host", "supplier-host")
			.replace("request-1:BORROWER", "request-1:SUPPLIER");
	}

	static String validItemCheckedOut() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemCheckedOut>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>borrower-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <UserId>
			      <AgencyId>borrower-host</AgencyId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <RequestId>
			      <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
			    </RequestId>
			    <DateDue>2026-07-17T12:06:00Z</DateDue>
			  </ItemCheckedOut>
			</NCIPMessage>
			""";
	}

	static String validItemRequested() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" xmlns:openrs="https://openrs.org/ncip/fallback-host" ncip:version="2.02">
			  <ItemRequested>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <UserId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <BibliographicId>
			      <BibliographicRecordId>
			        <BibliographicRecordIdentifier>bib-1</BibliographicRecordIdentifier>
			        <AgencyId>supplier-host</AgencyId>
			      </BibliographicRecordId>
			    </BibliographicId>
			    <RequestId>
			      <RequestIdentifierValue>request-1:SUPPLIER</RequestIdentifierValue>
			    </RequestId>
			    <RequestType>Hold</RequestType>
			    <RequestScopeType>Bibliographic Item</RequestScopeType>
			    <Ext>
			      <openrs:FallbackHostSelectedItem>
			        <openrs:SelectedItemBarcode>item-1</openrs:SelectedItemBarcode>
			      </openrs:FallbackHostSelectedItem>
			    </Ext>
			  </ItemRequested>
			</NCIPMessage>
			""";
	}

	static String validCancelRequestItem() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" xmlns:openrs="https://openrs.org/ncip/fallback-host" ncip:version="2.02">
			  <CancelRequestItem>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </InitiationHeader>
			    <UserId>
			      <UserIdentifierValue>user-1</UserIdentifierValue>
			    </UserId>
			    <RequestId>
			      <RequestIdentifierValue>request-1:SUPPLIER</RequestIdentifierValue>
			    </RequestId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			    <RequestType>Hold</RequestType>
			    <RequestScopeType>Bibliographic Item</RequestScopeType>
			    <Ext>
			      <openrs:FallbackHostCancelReason>
			        <openrs:ReasonCode>NOT_ON_SHELF</openrs:ReasonCode>
			        <openrs:ProcessingNote>Fallback Host not supplied</openrs:ProcessingNote>
			      </openrs:FallbackHostCancelReason>
			    </Ext>
			  </CancelRequestItem>
			</NCIPMessage>
			""";
	}

	static String validAcceptItemResponse() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <AcceptItemResponse>
			    <ResponseHeader>
			      <FromAgencyId>
			        <AgencyId>borrower-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb-host</AgencyId>
			      </ToAgencyId>
			    </ResponseHeader>
			    <RequestId>
			      <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
			    </RequestId>
			    <ItemId>
			      <ItemIdentifierValue>item-1</ItemIdentifierValue>
			    </ItemId>
			  </AcceptItemResponse>
			</NCIPMessage>
			""";
	}
}
