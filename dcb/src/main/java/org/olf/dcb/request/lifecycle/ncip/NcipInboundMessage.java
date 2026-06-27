package org.olf.dcb.request.lifecycle.ncip;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record NcipInboundMessage(
	String messageKind,
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
