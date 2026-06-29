package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

class NcipInboundXmlMapperTests {
	@Test
	void mapsItemShippedXmlToNcipInboundMessage() {
		final var message = new NcipInboundXmlMapper().map(
			NcipControllerTests.validItemShipped());

		assertThat(message.messageKind(), is(NcipProtocol.ITEM_SHIPPED));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("SHIPPED"));
		assertThat(message.rawStatus(), is("ItemShipped"));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.messageTimestamp(),
			is(Instant.parse("2026-06-26T12:03:00Z")));
	}

	@Test
	void mapsRequestItemResponseXmlToSupplierPlacementEvidence() {
		final var message = new NcipInboundXmlMapper().map(
			NcipControllerTests.validRequestItemResponse());

		assertThat(message.messageKind(),
			is(NcipProtocol.REQUEST_ITEM_RESPONSE));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.REQUEST_ITEM_RESPONSE));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void mapsRequestItemResponseProblemToSupplierMissingEvidence() {
		final var message = new NcipInboundXmlMapper().map("""
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <RequestItemResponse>
			    <ResponseHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			    </ResponseHeader>
			    <RequestId>
			      <RequestIdentifierValue>request-1:SUPPLIER</RequestIdentifierValue>
			    </RequestId>
			    <Problem>
			      <ProblemType>Processing Error</ProblemType>
			      <ProblemDetail>Fallback Host not supplied: NOT_ON_SHELF</ProblemDetail>
			    </Problem>
			  </RequestItemResponse>
			</NCIPMessage>
			""");

		assertThat(message.messageKind(),
			is(NcipProtocol.REQUEST_ITEM_RESPONSE));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.correlationId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("MISSING"));
		assertThat(message.rawStatus(),
			is("RequestItemResponse:Problem:Fallback Host not supplied: NOT_ON_SHELF"));
	}

	@Test
	void mapsItemRequestedToSupplierConfirmedEvidence() {
		final var message = new NcipInboundXmlMapper().map("""
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" xmlns:openrs="https://openrs.org/ncip/fallback-host" ncip:version="2.02">
			  <ItemRequested>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb</AgencyId>
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
			""");

		assertThat(message.messageKind(), is(NcipProtocol.ITEM_REQUESTED));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_REQUESTED));
		assertThat(message.itemBarcode(), is("item-1"));
	}

	@Test
	void mapsCancelRequestItemToSupplierMissingEvidence() {
		final var message = new NcipInboundXmlMapper().map("""
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" xmlns:openrs="https://openrs.org/ncip/fallback-host" ncip:version="2.02">
			  <CancelRequestItem>
			    <InitiationHeader>
			      <FromAgencyId>
			        <AgencyId>supplier-host</AgencyId>
			      </FromAgencyId>
			      <ToAgencyId>
			        <AgencyId>dcb</AgencyId>
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
			""");

		assertThat(message.messageKind(), is(NcipProtocol.CANCEL_REQUEST_ITEM));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("request-1:SUPPLIER"));
		assertThat(message.status(), is("MISSING"));
		assertThat(message.rawStatus(), is("CancelRequestItem:NOT_ON_SHELF"));
		assertThat(message.itemBarcode(), is("item-1"));
	}

	@Test
	void mapsAcceptItemResponseXmlToBorrowerPlacementEvidence() {
		final var message = new NcipInboundXmlMapper().map(
			NcipControllerTests.validAcceptItemResponse());

		assertThat(message.messageKind(),
			is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("borrower-host"));
		assertThat(message.hostRequestId(), is("request-1:BORROWER"));
		assertThat(message.correlationId(), is("request-1:BORROWER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
		assertThat(message.itemId(), is("item-1"));
	}

	@Test
	void rejectsNcipResponseProblems() {
		final var error = assertThrows(NcipProblemException.class,
			() -> new NcipInboundXmlMapper().map("""
				<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
				  <AcceptItemResponse>
				    <Problem>
				      <ProblemType>Processing Error</ProblemType>
				      <ProblemDetail>Rejected by peer</ProblemDetail>
				    </Problem>
				  </AcceptItemResponse>
				</NCIPMessage>
				"""));

		assertThat(error.getMessage(),
			is("AcceptItemResponse contains Problem: Rejected by peer"));
	}

	@Test
	void rejectsUnsupportedNcipMessage() {
		final var error = assertThrows(NcipProblemException.class,
			() -> new NcipInboundXmlMapper().map("""
				<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
				  <LookupItemResponse>
				    <Problem>
				      <ProblemType>Processing Error</ProblemType>
				    </Problem>
				  </LookupItemResponse>
				</NCIPMessage>
				"""));

		assertThat(error.getMessage(),
			is("Unsupported NCIP message: LookupItemResponse"));
	}
}
