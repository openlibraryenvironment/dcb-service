package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;

@Prototype
public class NcipInboundMessageMapper {
	public InboundLifecycleMessage map(NcipInboundMessage message) {
		return new InboundLifecycleMessage(
			NcipProtocol.PROTOCOL,
			message.role(),
			message.operation(),
			resourceFor(message),
			message.hostLmsCode(),
			message.hostRequestId(),
			message.correlationId(),
			statusFor(message),
			message.rawStatus(),
			message.itemId(),
			message.itemBarcode(),
			message.messageTimestamp(),
			message.rawMessageReference(),
			message.protocolProperties());
	}

	private static LifecycleEvidenceResource resourceFor(
		NcipInboundMessage message) {

		return NcipProtocol.ITEM_SHIPPED.equals(message.messageKind())
			|| NcipProtocol.ITEM_RECEIVED.equals(message.messageKind())
			|| NcipProtocol.ITEM_CHECKED_IN.equals(message.messageKind())
			|| NcipProtocol.ITEM_CHECKED_OUT.equals(message.messageKind())
			? LifecycleEvidenceResource.ITEM
			: LifecycleEvidenceResource.REQUEST;
	}

	private static String statusFor(NcipInboundMessage message) {
		return switch (message.messageKind()) {
			case NcipProtocol.ITEM_SHIPPED -> HostLmsItem.ITEM_TRANSIT;
			case NcipProtocol.ITEM_RECEIVED -> HostLmsItem.ITEM_RECEIVED;
			case NcipProtocol.ITEM_CHECKED_IN -> checkedInStatusFor(message);
			case NcipProtocol.ITEM_CHECKED_OUT -> HostLmsItem.ITEM_LOANED;
			default -> message.status();
		};
	}

	private static String checkedInStatusFor(NcipInboundMessage message) {
		return message.role() == LifecycleRole.SUPPLIER
			? HostLmsItem.ITEM_RECEIVED
			: HostLmsItem.ITEM_ON_HOLDSHELF;
	}
}
