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

		assertThat(message.messageKind(), is("ItemShipped"));
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
	void rejectsUnsupportedNcipMessage() {
		final var error = assertThrows(NcipProblemException.class,
			() -> new NcipInboundXmlMapper().map("""
				<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
				  <AcceptItemResponse>
				    <RequestId>
				      <RequestIdentifierValue>request-1:BORROWER</RequestIdentifierValue>
				    </RequestId>
				  </AcceptItemResponse>
				</NCIPMessage>
				"""));

		assertThat(error.getMessage(), is("Unsupported NCIP message: AcceptItemResponse"));
	}
}
