package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.interaction.HostLmsItem;
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
			message.rawMessageReference());
	}

	private static LifecycleEvidenceResource resourceFor(
		NcipInboundMessage message) {

		return NcipProtocol.ITEM_SHIPPED.equals(message.messageKind())
			? LifecycleEvidenceResource.ITEM
			: LifecycleEvidenceResource.REQUEST;
	}

	private static String statusFor(NcipInboundMessage message) {
		return NcipProtocol.ITEM_SHIPPED.equals(message.messageKind())
			? HostLmsItem.ITEM_TRANSIT
			: message.status();
	}
}
