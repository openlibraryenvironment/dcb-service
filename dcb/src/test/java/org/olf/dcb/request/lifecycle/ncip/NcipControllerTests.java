package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import reactor.core.publisher.Mono;

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
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("SHIPPED"));
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

	private static NcipController controllerWith(
		InboundLifecycleMessageHandler handler) {

		return new NcipController(
			handler,
			new NcipInboundXmlMapper(),
			new NcipResponseBuilder(),
			new NcipSchemaValidator(NcipSchemaPath.schemaPath()));
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
}
