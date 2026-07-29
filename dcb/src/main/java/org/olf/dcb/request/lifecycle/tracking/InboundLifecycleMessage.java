package org.olf.dcb.request.lifecycle.tracking;

import java.time.Instant;
import java.util.Map;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceResource;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record InboundLifecycleMessage(
	String protocol,
	LifecycleRole role,
	LifecycleOperation operation,
	LifecycleEvidenceResource resource,
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

	public InboundLifecycleMessage(
		String protocol,
		LifecycleRole role,
		LifecycleOperation operation,
		LifecycleEvidenceResource resource,
		String hostLmsCode,
		String hostRequestId,
		String correlationId,
		String status,
		String rawStatus,
		String itemId,
		String itemBarcode,
		Instant messageTimestamp,
		String rawMessageReference) {

		this(protocol, role, operation, resource, hostLmsCode, hostRequestId,
			correlationId, status, rawStatus, itemId, itemBarcode,
			messageTimestamp, rawMessageReference, Map.of());
	}
}
