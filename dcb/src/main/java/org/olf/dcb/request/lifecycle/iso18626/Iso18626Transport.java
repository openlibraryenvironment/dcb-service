package org.olf.dcb.request.lifecycle.iso18626;

import reactor.core.publisher.Mono;

public interface Iso18626Transport {
	Mono<Iso18626TransportResponse> send(Iso18626TransportRequest request);
}
