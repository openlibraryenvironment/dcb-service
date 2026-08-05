package org.olf.dcb.request.lifecycle;

public record DeclarativeTransportRequest(
	String protocol,
	LifecycleRole role,
	LifecycleOperation operation,
	String hostLmsCode,
	String agencyCode,
	String correlationId,
	String messageKind,
	String payload) {
}
