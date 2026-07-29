package org.olf.dcb.request.lifecycle;

import reactor.core.publisher.Mono;

public interface DeclarativeRequestTransport {
	Mono<DeclarativeTransportResponse> send(DeclarativeTransportRequest request);
}
