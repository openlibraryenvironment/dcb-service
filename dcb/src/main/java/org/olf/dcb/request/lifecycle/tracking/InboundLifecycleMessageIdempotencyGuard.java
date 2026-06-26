package org.olf.dcb.request.lifecycle.tracking;

import io.micronaut.context.annotation.Prototype;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Prototype
public class InboundLifecycleMessageIdempotencyGuard {
	private final Set<String> seenMessageKeys = ConcurrentHashMap.newKeySet();

	public boolean firstSeen(InboundLifecycleMessage message) {
		return seenMessageKeys.add(keyFor(message));
	}

	private static String keyFor(InboundLifecycleMessage message) {
		return String.join("|",
			Objects.toString(message.protocol(), ""),
			Objects.toString(message.role(), ""),
			Objects.toString(message.correlationId(), ""),
			Objects.toString(message.status(), ""),
			Objects.toString(message.messageTimestamp(), ""),
			Objects.toString(message.rawMessageReference(), ""));
	}
}
