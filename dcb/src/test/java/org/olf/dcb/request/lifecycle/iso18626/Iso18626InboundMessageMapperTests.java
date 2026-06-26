package org.olf.dcb.request.lifecycle.iso18626;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

class Iso18626InboundMessageMapperTests {
	@Test
	void mapsIsoAdapterOutputToCanonicalInboundLifecycleMessage() {
		final var timestamp = Instant.parse("2026-06-26T12:03:00Z");
		final var mapper = new Iso18626InboundMessageMapper();

		final var message = mapper.map(new Iso18626InboundMessage(
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			"patron-request-id:SUPPLIER",
			"CONFIRMED",
			"confirmed",
			"item-1",
			"barcode-1",
			timestamp,
			"raw-message-1"));

		assertThat(message.protocol(), is("iso18626"));
		assertThat(message.role(), is(LifecycleRole.SUPPLIER));
		assertThat(message.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("supplier-remote-request"));
		assertThat(message.correlationId(), is("patron-request-id:SUPPLIER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is("confirmed"));
		assertThat(message.itemId(), is("item-1"));
		assertThat(message.itemBarcode(), is("barcode-1"));
		assertThat(message.messageTimestamp(), is(timestamp));
		assertThat(message.rawMessageReference(), is("raw-message-1"));
	}
}
