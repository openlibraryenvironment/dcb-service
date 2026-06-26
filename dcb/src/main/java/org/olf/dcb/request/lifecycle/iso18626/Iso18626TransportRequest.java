package org.olf.dcb.request.lifecycle.iso18626;

import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record Iso18626TransportRequest(
	LifecycleRole role,
	LifecycleOperation operation,
	String hostLmsCode,
	String agencyCode,
	String correlationId,
	String messageKind,
	String payload) {
}
