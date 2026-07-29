package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;

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
		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.hostLmsCode(), is("supplier-host"));
		assertThat(message.hostRequestId(), is("supplier-remote-request"));
		assertThat(message.correlationId(), is("patron-request-id:SUPPLIER"));
		assertThat(message.status(), is(HostLmsItem.ITEM_TRANSIT));
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
			"CONFIRMED",
			"AcceptItemResponse",
			null,
			null,
			timestamp,
			"raw-message-2"));

		assertThat(message.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(message.role(), is(LifecycleRole.BORROWER));
		assertThat(message.resource(), is(LifecycleEvidenceResource.REQUEST));
		assertThat(message.hostRequestId(), is("borrower-remote-request"));
		assertThat(message.correlationId(), is("patron-request-id:BORROWER"));
		assertThat(message.status(), is("CONFIRMED"));
		assertThat(message.rawStatus(), is("AcceptItemResponse"));
		assertThat(message.rawMessageReference(), is("raw-message-2"));
	}

	@Test
	void mapsBorrowerItemReceivedToCanonicalItemEvidence() {
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			NcipProtocol.ITEM_RECEIVED,
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			"patron-request-id:BORROWER",
			"RECEIVED",
			NcipProtocol.ITEM_RECEIVED,
			"item-1",
			"barcode-1",
			null,
			"raw-message-3"));

		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.status(), is(HostLmsItem.ITEM_RECEIVED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_RECEIVED));
	}

	@Test
	void mapsBorrowerItemCheckedInToCanonicalHoldShelfEvidence() {
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			NcipProtocol.ITEM_CHECKED_IN,
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			"patron-request-id:BORROWER",
			"CHECKED_IN",
			NcipProtocol.ITEM_CHECKED_IN,
			"item-1",
			"barcode-1",
			null,
			"raw-message-4"));

		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.status(), is(HostLmsItem.ITEM_ON_HOLDSHELF));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_IN));
	}

	@Test
	void mapsSupplierItemCheckedInToCanonicalReceivedEvidence() {
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			NcipProtocol.ITEM_CHECKED_IN,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			"patron-request-id:SUPPLIER",
			"CHECKED_IN",
			NcipProtocol.ITEM_CHECKED_IN,
			"item-1",
			"barcode-1",
			null,
			"raw-message-5"));

		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.status(), is(HostLmsItem.ITEM_RECEIVED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_IN));
	}

	@Test
	void mapsBorrowerItemCheckedOutToCanonicalLoanedEvidence() {
		final var mapper = new NcipInboundMessageMapper();

		final var message = mapper.map(new NcipInboundMessage(
			NcipProtocol.ITEM_CHECKED_OUT,
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			"patron-request-id:BORROWER",
			"CHECKED_OUT",
			NcipProtocol.ITEM_CHECKED_OUT,
			"item-1",
			"barcode-1",
			null,
			"raw-message-6"));

		assertThat(message.resource(), is(LifecycleEvidenceResource.ITEM));
		assertThat(message.status(), is(HostLmsItem.ITEM_LOANED));
		assertThat(message.rawStatus(), is(NcipProtocol.ITEM_CHECKED_OUT));
	}
}
