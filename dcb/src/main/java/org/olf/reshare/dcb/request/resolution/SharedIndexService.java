package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface SharedIndexService {
	Mono<ClusteredBib> findClusteredBib(UUID bibClusterId);
}

