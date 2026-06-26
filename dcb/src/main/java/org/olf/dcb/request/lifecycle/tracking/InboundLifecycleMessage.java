package org.olf.dcb.request.lifecycle.tracking;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record InboundLifecycleMessage(
	String protocol,
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
