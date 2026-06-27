package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;

@Prototype
public class NcipInboundMessageMapper {
	public InboundLifecycleMessage map(NcipInboundMessage message) {
		return new InboundLifecycleMessage(
			NcipProtocol.PROTOCOL,
			message.role(),
			message.operation(),
			message.hostLmsCode(),
			message.hostRequestId(),
			message.correlationId(),
			message.status(),
			message.rawStatus(),
			message.itemId(),
			message.itemBarcode(),
			message.messageTimestamp(),
			message.rawMessageReference());
	}
}
