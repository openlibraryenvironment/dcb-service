package org.olf.dcb.request.lifecycle.iso18626;

public record Iso18626TransportResponse(
	String remoteRequestId,
	String status,
	String rawStatus,
	String rawMessageReference) {
}
