package org.olf.dcb.request.lifecycle.evidence;

import java.time.Instant;
import java.util.Map;
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
		String rawMessageReference,
		String trackingResourceType,
		String trackingResourceId,
		String fromStatus,
		Integer fromRenewalCount,
		Integer toRenewalCount,
		Integer fromHoldCount,
		Integer toHoldCount,
		Boolean renewable,
		Map<String, Object> trackingProperties) {

	public LifecycleEvidence(
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

		this(source, protocol, role, operation, resource, hostLmsCode,
			hostRequestId, correlationId, status, rawStatus, itemId, itemBarcode,
			messageTimestamp, rawMessageReference, null, null, null, null, null,
			null, null, null, null);
	}
}
