package org.olf.dcb.request.lifecycle.ncip;

import java.time.Instant;
import java.util.Map;
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
	String rawMessageReference,
	Map<String, Object> protocolProperties) {

	public NcipInboundMessage(
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

		this(messageKind, role, operation, hostLmsCode, hostRequestId,
			correlationId, status, rawStatus, itemId, itemBarcode,
			messageTimestamp, rawMessageReference, Map.of());
	}
}
