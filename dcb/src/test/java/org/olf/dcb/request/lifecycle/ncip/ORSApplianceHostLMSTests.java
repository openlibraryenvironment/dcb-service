package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.AbstractHostLmsClient;
import org.olf.dcb.core.interaction.CanPlaceBorrowingAgencyRequest;
import org.olf.dcb.core.interaction.CanPlaceSupplyingAgencyRequest;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import reactor.core.publisher.Mono;

class ORSApplianceHostLMSTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(
		NcipSchemaPath.schemaPath());

	@Test
	void exposesPlacementCapabilitiesAndNcipEndpointSetting() {
		final var client = clientWith(new CapturingTransport(
			response("remote-request")));

		assertThat(client instanceof CanPlaceSupplyingAgencyRequest, is(true));
		assertThat(client instanceof CanPlaceBorrowingAgencyRequest, is(true));
		assertThat(client.getSettings().stream()
			.map(HostLmsPropertyDefinition::getName)
			.toList(), hasItem(ORSApplianceHostLMS.NCIP_ENDPOINT_URL_KEY));
	}

	@Test
	void placesSupplyingAgencyRequestUsingNcipRequestItem() {
		final var transport = new CapturingTransport(response("supplier-remote"));
		final var client = clientWith(transport);

		final var localRequest = singleValueFrom(
			client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.patronRequestId("request-1")
					.localPatronBarcode("patron-1")
					.localBibId("bib-1")
					.supplyingAgencyCode("supplier-agency")
					.build()));
		final var request = transport.onlyRequest();

		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.SUPPLIER));
		assertThat(request.hostLmsCode(), is("ors-host"));
		assertThat(request.correlationId(), is("request-1:SUPPLIER"));
		assertThat(request.messageKind(), is(NcipProtocol.REQUEST_ITEM));
		assertThat(request.payload(), containsString("<RequestItem"));
		assertThat(request.payload(),
			containsString("<UserIdentifierValue>patron-1</UserIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<BibliographicRecordIdentifier>bib-1</BibliographicRecordIdentifier>"));
		assertDoesNotThrow(() -> validator.validate(request.payload()));
		assertThat(localRequest.getLocalId(), is("supplier-remote"));
		assertThat(localRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(localRequest.getRawLocalStatus(),
			is(NcipProtocol.REQUEST_ITEM_RESPONSE));
	}

	@Test
	void placesBorrowingAgencyRequestUsingNcipAcceptItem() {
		final var transport = new CapturingTransport(response("borrower-remote"));
		final var client = clientWith(transport);

		final var localRequest = singleValueFrom(
			client.placeHoldRequestAtBorrowingAgency(
				PlaceHoldRequestParameters.builder()
					.patronRequestId("request-1")
					.localPatronBarcode("patron-1")
					.supplyingLocalItemId("item-1")
					.build()));
		final var request = transport.onlyRequest();

		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.BORROWER));
		assertThat(request.hostLmsCode(), is("ors-host"));
		assertThat(request.correlationId(), is("request-1:BORROWER"));
		assertThat(request.messageKind(), is(NcipProtocol.ACCEPT_ITEM));
		assertThat(request.payload(), containsString("<AcceptItem"));
		assertThat(request.payload(),
			containsString("<RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<ItemIdentifierValue>item-1</ItemIdentifierValue>"));
		assertDoesNotThrow(() -> validator.validate(request.payload()));
		assertThat(localRequest.getLocalId(), is("borrower-remote"));
		assertThat(localRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(localRequest.getRawLocalStatus(),
			is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
	}

	@Test
	void baseHostLmsClientReturnsEmptyForUnsupportedMethods() {
		final var client = new BaseOnlyClient(hostLms());

		assertThat(client.getItemByBarcode("barcode").blockOptional().isEmpty(),
			is(true));
	}

	private static ORSApplianceHostLMS clientWith(
		DeclarativeRequestTransport transport) {

		return new ORSApplianceHostLMS(
			hostLms(),
			transport,
			new NcipPayloadBuilder());
	}

	private static DataHostLms hostLms() {
		return DataHostLms.builder()
			.code("ors-host")
			.name("ORS Host")
			.clientConfig(Map.of(
				ORSApplianceHostLMS.NCIP_ENDPOINT_URL_KEY,
				"https://ors.example.org/ncip/v2_02"))
			.build();
	}

	private static DeclarativeTransportResponse response(String remoteRequestId) {
		final var responseKind = remoteRequestId.startsWith("borrower")
			? NcipProtocol.ACCEPT_ITEM_RESPONSE
			: NcipProtocol.REQUEST_ITEM_RESPONSE;

		return new DeclarativeTransportResponse(
			remoteRequestId,
			"CONFIRMED",
			responseKind,
			"raw-message");
	}

	private static class CapturingTransport implements DeclarativeRequestTransport {
		private final DeclarativeTransportResponse response;
		private final List<DeclarativeTransportRequest> requests = new ArrayList<>();

		CapturingTransport(DeclarativeTransportResponse response) {
			this.response = response;
		}

		@Override
		public Mono<DeclarativeTransportResponse> send(
			DeclarativeTransportRequest request) {

			requests.add(request);
			return Mono.just(response);
		}

		DeclarativeTransportRequest onlyRequest() {
			assertThat(requests.size(), is(1));
			return requests.getFirst();
		}
	}

	private static class BaseOnlyClient extends AbstractHostLmsClient {
		BaseOnlyClient(HostLms hostLms) {
			super(hostLms);
		}
	}
}
