package org.olf.dcb.request.lifecycle.evidence;

import java.time.Instant;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record LifecycleEvidence(
	LifecycleEvidenceSource source,
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
}

