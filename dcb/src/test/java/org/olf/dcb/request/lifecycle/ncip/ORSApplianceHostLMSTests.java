package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.AbstractHostLmsClient;
import org.olf.dcb.core.interaction.CanPlaceBorrowingAgencyRequest;
import org.olf.dcb.core.interaction.CanPlaceSupplyingAgencyRequest;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.LocationRepository;
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
					.localItemId("item-1")
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
		assertThat(request.payload(),
			containsString("<ItemIdentifierValue>item-1</ItemIdentifierValue>"));
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
	void authenticatesPatronUsingNcipLookupUserAuthenticationInput() {
		final var httpClient = mock(HttpClient.class);
		final var client = clientWith(
			new CapturingTransport(response("remote-request")),
			httpClient);
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(validLookupUserResponse())));

		final var patron = singleValueFrom(client.patronAuth(
			"BASIC/BARCODE+PIN",
			"rincewind",
			"RW"));

		final var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).exchange(requestCaptor.capture(), eq(Argument.of(String.class)));
		final var body = (String) requestCaptor.getValue().getBody(String.class).orElseThrow();

		assertThat(body, containsString("<LookupUser"));
		assertThat(body, containsString("<AuthenticationInputType>Username</AuthenticationInputType>"));
		assertThat(body, containsString("<AuthenticationInputData>rincewind</AuthenticationInputData>"));
		assertThat(body, containsString("<AuthenticationInputType>Password</AuthenticationInputType>"));
		assertThat(body, containsString("<AuthenticationInputData>RW</AuthenticationInputData>"));
		assertDoesNotThrow(() -> validator.validate(body));
		assertThat(patron.getFirstLocalId(), is("rincewind-user-0001"));
		assertThat(patron.getLocalNames(), is(List.of("Rincewind")));
		assertThat(patron.getLocalHomeLibraryCode(), is("unseen-main"));
	}

	@Test
	void looksUpPatronByUsernameUsingNcipLookupUserUserId() {
		final var httpClient = mock(HttpClient.class);
		final var client = clientWith(
			new CapturingTransport(response("remote-request")),
			httpClient);
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(validLookupUserResponse())));

		final var patron = singleValueFrom(client.getPatronByUsername("rincewind"));

		final var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).exchange(requestCaptor.capture(), eq(Argument.of(String.class)));
		final var body = (String) requestCaptor.getValue().getBody(String.class).orElseThrow();

		assertThat(body, containsString("<LookupUser"));
		assertThat(body, containsString("<UserIdentifierValue>rincewind</UserIdentifierValue>"));
		assertDoesNotThrow(() -> validator.validate(body));
		assertThat(patron.getFirstLocalId(), is("rincewind-user-0001"));
	}

	@Test
	void getsItemsUsingNcipLookupItemSet() {
		final var httpClient = mock(HttpClient.class);
		final var agencyRepository = mock(AgencyRepository.class);
		final var locationRepository = mock(LocationRepository.class);
		final var client = clientWith(
			new CapturingTransport(response("remote-request")),
			httpClient,
			agencyRepository,
			locationRepository);
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(validLookupItemSetResponse())));
		DataHostLms hostLms = hostLms();
		DataAgency agency = DataAgency.builder()
			.id(UUID.randomUUID())
			.code("ors-unseen")
			.name("The Unseen University")
			.hostLms(DataHostLms.builder().id(UUID.randomUUID()).build())
			.isSupplyingAgency(true)
			.build();
		Location location = Location.builder()
			.id(UUID.randomUUID())
			.code("UNSEEN-MAIN-1")
			.name("Unseen Main Library")
			.type("PICKUP")
			.hostSystem(hostLms)
			.agency(agency)
			.isPickup(true)
			.build();
		when(agencyRepository.findOneByCode("ors-unseen"))
			.thenReturn(Mono.just(agency));
		when(locationRepository.findOneByCode("UNSEEN-MAIN-1"))
			.thenReturn(Mono.just(location));

		final var items = singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId("uu-fhs-0001")
			.build()));

		final var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).exchange(requestCaptor.capture(), eq(Argument.of(String.class)));
		final var body = (String) requestCaptor.getValue().getBody(String.class).orElseThrow();

		assertThat(body, containsString("<LookupItemSet"));
		assertThat(body, containsString("<BibliographicRecordIdentifier>uu-fhs-0001</BibliographicRecordIdentifier>"));
		assertDoesNotThrow(() -> validator.validate(body));
		assertThat(items.size(), is(1));
		assertThat(items.getFirst().getLocalId(), is("UU-FHS-0001"));
		assertThat(items.getFirst().getLocalBibId(), is("uu-fhs-0001"));
		assertThat(items.getFirst().getBarcode(), is("UU-FHS-0001"));
		assertThat(items.getFirst().getLocation().getCode(), is("UNSEEN-MAIN-1"));
		assertThat(items.getFirst().getAgencyCode(), is("ors-unseen"));
		assertThat(items.getFirst().getHostLmsCode(), is("ors-host"));
		assertThat(items.getFirst().getStatus().getCode(), is(ItemStatusCode.AVAILABLE));
		assertThat(items.getFirst().getCanonicalItemType(), is("CIRC"));
	}

	@Test
	void getsItemsUsingNcipLookupItemSetWhenLocationIsNotConfiguredInDcb() {
		final var httpClient = mock(HttpClient.class);
		final var agencyRepository = mock(AgencyRepository.class);
		final var locationRepository = mock(LocationRepository.class);
		final var client = clientWith(
			new CapturingTransport(response("remote-request")),
			httpClient,
			agencyRepository,
			locationRepository);
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(validLookupItemSetResponse())));
		DataAgency agency = DataAgency.builder()
			.id(UUID.randomUUID())
			.code("ors-unseen")
			.name("The Unseen University")
			.hostLms(DataHostLms.builder().id(UUID.randomUUID()).code("ors-host").build())
			.isSupplyingAgency(true)
			.build();
		when(agencyRepository.findOneByCode("ors-unseen"))
			.thenReturn(Mono.just(agency));
		when(locationRepository.findOneByCode("UNSEEN-MAIN-1"))
			.thenReturn(Mono.empty());

		final var items = singleValueFrom(client.getItems(BibRecord.builder()
			.sourceRecordId("uu-fhs-0001")
			.build()));

		assertThat(items.size(), is(1));
		assertThat(items.getFirst().getLocalId(), is("UU-FHS-0001"));
		assertThat(items.getFirst().getLocation().getCode(), is("UNSEEN-MAIN-1"));
		assertThat(items.getFirst().getLocation().getName(), is("UNSEEN-MAIN-1"));
		assertThat(items.getFirst().getAgencyCode(), is("ors-unseen"));
	}

	@Test
	void baseHostLmsClientReturnsEmptyForUnsupportedMethods() {
		final var client = new BaseOnlyClient(hostLms());

		assertThat(client.getItemByBarcode("barcode").blockOptional().isEmpty(),
			is(true));
	}

	private static ORSApplianceHostLMS clientWith(
		DeclarativeRequestTransport transport) {

		return clientWith(transport, mock(HttpClient.class));
	}

	private static ORSApplianceHostLMS clientWith(
		DeclarativeRequestTransport transport,
		HttpClient httpClient) {

		return clientWith(
			transport,
			httpClient,
			mock(AgencyRepository.class),
			mock(LocationRepository.class));
	}

	private static ORSApplianceHostLMS clientWith(
		DeclarativeRequestTransport transport,
		HttpClient httpClient,
		AgencyRepository agencyRepository,
		LocationRepository locationRepository) {

		return new ORSApplianceHostLMS(
			hostLms(),
			transport,
			new NcipPayloadBuilder(),
			httpClient,
			agencyRepository,
			locationRepository);
	}

	private static DataHostLms hostLms() {
		return DataHostLms.builder()
			.code("ors-host")
			.name("ORS Host")
			.clientConfig(Map.of(
				ORSApplianceHostLMS.NCIP_ENDPOINT_URL_KEY,
				"https://ors.example.org/ncip/v2_02",
				"default-agency-code",
				"ors-unseen"))
			.build();
	}

	private static String validLookupUserResponse() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <LookupUserResponse>
			    <ResponseHeader>
			      <FromAgencyId>
			        <AgencyId>ors-unseen</AgencyId>
			      </FromAgencyId>
			    </ResponseHeader>
			    <UserId>
			      <AgencyId>ors-unseen</AgencyId>
			      <UserIdentifierValue>rincewind-user-0001</UserIdentifierValue>
			    </UserId>
			    <UserOptionalFields>
			      <NameInformation>
			        <PersonalNameInformation>
			          <UnstructuredPersonalUserName>Rincewind</UnstructuredPersonalUserName>
			        </PersonalNameInformation>
			      </NameInformation>
			      <UserPrivilege>
			        <AgencyId>unseen-main</AgencyId>
			        <AgencyUserPrivilegeType>Patron</AgencyUserPrivilegeType>
			      </UserPrivilege>
			    </UserOptionalFields>
			  </LookupUserResponse>
			</NCIPMessage>
			""";
	}

	private static String validLookupItemSetResponse() {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <LookupItemSetResponse>
			    <ResponseHeader>
			      <FromAgencyId>
			        <AgencyId>ors-unseen</AgencyId>
			      </FromAgencyId>
			    </ResponseHeader>
			    <BibInformation>
			      <BibliographicId>
			        <BibliographicRecordId>
			          <BibliographicRecordIdentifier>uu-fhs-0001</BibliographicRecordIdentifier>
			          <AgencyId>ors-unseen</AgencyId>
			        </BibliographicRecordId>
			      </BibliographicId>
			      <HoldingsSet>
			        <ItemInformation>
			          <ItemId>
			            <ItemIdentifierValue>UU-FHS-0001</ItemIdentifierValue>
			          </ItemId>
			          <ItemOptionalFields>
			            <CirculationStatus>Available</CirculationStatus>
			            <ItemDescription>
			              <CallNumber>PRATCHETT COLOUR</CallNumber>
			            </ItemDescription>
			            <Location>
			              <LocationType>Permanent Location</LocationType>
			              <LocationName>
			                <LocationNameInstance>
			                  <LocationNameLevel>1</LocationNameLevel>
			                  <LocationNameValue>UNSEEN-MAIN-1</LocationNameValue>
			                </LocationNameInstance>
			              </LocationName>
			            </Location>
			          </ItemOptionalFields>
			        </ItemInformation>
			      </HoldingsSet>
			    </BibInformation>
			  </LookupItemSetResponse>
			</NCIPMessage>
			""";
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
