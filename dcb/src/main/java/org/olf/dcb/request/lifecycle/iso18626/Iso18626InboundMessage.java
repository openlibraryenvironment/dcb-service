package org.olf.dcb.request.lifecycle.iso18626;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record Iso18626InboundMessage(
	LifecycleRole role,
	LifecycleOperation operation,
	String hostLmsCode,
	String hostRequestId,
	String correlationId,
	String status,
	String rawStatus,
	String itemId,
	String itemBarcode,
	Instant messageTimestamp,
	String rawMessageReference) {
}
