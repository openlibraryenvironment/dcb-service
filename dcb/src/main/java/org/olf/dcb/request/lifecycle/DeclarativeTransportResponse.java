package org.olf.dcb.request.lifecycle;

public record DeclarativeTransportResponse(
	String remoteRequestId,
	String status,
	String rawStatus,
	String rawMessageReference) {
}
