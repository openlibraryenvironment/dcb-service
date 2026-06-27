package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ORSApplianceHostLMSContextTests {
	private static final String HOST_LMS_CODE = "ors-ncip-host";
	private static final String NCIP_HOST = "ors-ncip.example.org";
	private static final String NCIP_PATH = "/ncip/v2_02";
	private static final String NCIP_ENDPOINT_URL = "https://" + NCIP_HOST
		+ NCIP_PATH;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();
		hostLmsFixture.createORSApplianceHostLms(
			HOST_LMS_CODE,
			NCIP_ENDPOINT_URL);
	}

	@Test
	void resolvesNcipHostLmsClientAndPostsRequestItem(
		MockServerClient mockServerClient) {

		mockNcipResponse(mockServerClient, requestItemResponse());

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(
			client.placeHoldRequestAtSupplyingAgency(
				PlaceHoldRequestParameters.builder()
					.patronRequestId("request-1")
					.localPatronBarcode("patron-1")
					.localBibId("bib-1")
					.supplyingAgencyCode("supplier-agency")
					.build()));

		final var recordedRequests = recordedNcipRequests(mockServerClient);

		assertThat(client, instanceOf(ORSApplianceHostLMS.class));
		assertThat(placedRequest.getLocalId(), is("request-1:SUPPLIER"));
		assertThat(placedRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(placedRequest.getRawLocalStatus(),
			is(NcipProtocol.REQUEST_ITEM_RESPONSE));
		assertThat(recordedRequests.length, is(1));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<RequestItem"));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<UserIdentifierValue>patron-1</UserIdentifierValue>"));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<BibliographicRecordIdentifier>bib-1</BibliographicRecordIdentifier>"));
	}

	@Test
	void resolvesNcipHostLmsClientAndPostsAcceptItem(
		MockServerClient mockServerClient) {

		mockNcipResponse(mockServerClient, acceptItemResponse());

		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var placedRequest = singleValueFrom(
			client.placeHoldRequestAtBorrowingAgency(
				PlaceHoldRequestParameters.builder()
					.patronRequestId("request-1")
					.localPatronBarcode("patron-1")
					.supplyingLocalItemId("item-1")
					.build()));

		final var recordedRequests = recordedNcipRequests(mockServerClient);

		assertThat(client, instanceOf(ORSApplianceHostLMS.class));
		assertThat(placedRequest.getLocalId(), is("request-1:BORROWER"));
		assertThat(placedRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(placedRequest.getRawLocalStatus(),
			is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
		assertThat(recordedRequests.length, is(1));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<AcceptItem"));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>"));
		assertThat(recordedRequests[0].getBodyAsString(),
			containsString("<ItemIdentifierValue>item-1</ItemIdentifierValue>"));
	}

	private static void mockNcipResponse(
		MockServerClient mockServerClient,
		String body) {

		mockServerClient.when(request()
				.withHeader("host", NCIP_HOST)
				.withMethod("POST")
				.withPath(NCIP_PATH))
			.respond(response()
				.withStatusCode(200)
				.withHeader("Content-Type", "application/xml")
				.withBody(body));
	}

	private static org.mockserver.model.HttpRequest[] recordedNcipRequests(
		MockServerClient mockServerClient) {

		return mockServerClient.retrieveRecordedRequests(request()
			.withHeader("host", NCIP_HOST)
			.withMethod("POST")
			.withPath(NCIP_PATH));
	}

	private static String requestItemResponse() {
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
			  </RequestItemResponse>
			</NCIPMessage>
			""";
	}

	private static String acceptItemResponse() {
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
