package org.olf.dcb.request.lifecycle.iso18626;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;

@Prototype
public class Iso18626InboundMessageMapper {
	private static final String PROTOCOL = "iso18626";

	public InboundLifecycleMessage map(Iso18626InboundMessage message) {
		return new InboundLifecycleMessage(
			PROTOCOL,
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
