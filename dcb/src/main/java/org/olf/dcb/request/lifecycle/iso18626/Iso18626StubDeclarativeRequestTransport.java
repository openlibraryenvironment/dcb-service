package org.olf.dcb.request.lifecycle.iso18626;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import reactor.core.publisher.Mono;

@Prototype
public class Iso18626StubDeclarativeRequestTransport
	implements DeclarativeRequestTransport {
	@Override
	public Mono<DeclarativeTransportResponse> send(
		DeclarativeTransportRequest request) {

		return Mono.just(new DeclarativeTransportResponse(
			"%s:remote".formatted(request.correlationId()),
			"PLACED",
			"iso18626-stub-placed",
			"iso18626-spike-stub"));
	}
}
