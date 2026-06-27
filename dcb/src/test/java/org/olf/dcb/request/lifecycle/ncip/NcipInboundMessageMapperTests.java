package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

class NcipInboundMessageMapperTests {
	@Test
	void mapsSupplierItemShippedEvidenceToCanonicalInboundLifecycleMessage() {
		final var timestamp = Instant.parse("2026-06-26T12:03:00Z");
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			"ItemShipped",
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			"patron-request-id:SUPPLIER",
			"SHIPPED",
			"ItemShipped",
			"item-1",
			"barcode-1",
			timestamp,
			"raw-message-1"));

		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("supplier-remote-request"));
		assertThat(message.correlationId(), is("patron-request-id:SUPPLIER"));
		assertThat(message.status(), is("SHIPPED"));
		assertThat(message.rawStatus(), is("ItemShipped"));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.itemBarcode(), is("barcode-1"));
		assertThat(message.messageTimestamp(), is(timestamp));
		assertThat(message.rawMessageReference(), is("raw-message-1"));
	}

	@Test
	void mapsBorrowerAcceptItemResponseToCanonicalInboundLifecycleMessage() {
		final var timestamp = Instant.parse("2026-06-26T12:04:00Z");
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			"AcceptItemResponse",
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			"patron-request-id:BORROWER",
			"ACCEPTED",
			"AcceptItemResponse",
			null,
			null,
			timestamp,
			"raw-message-2"));

		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.hostRequestId(), is("borrower-remote-request"));
		assertThat(message.correlationId(), is("patron-request-id:BORROWER"));
		assertThat(message.status(), is("ACCEPTED"));
		assertThat(message.rawStatus(), is("AcceptItemResponse"));
		assertThat(message.rawMessageReference(), is("raw-message-2"));
	}
}
