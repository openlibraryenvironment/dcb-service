package org.olf.reshare.dcb.request.resolution;

import reactor.core.publisher.Mono;
import java.util.UUID;

public interface SharedIndexService {

	Mono<ClusteredBib> findClusteredBib(UUID uuid);

}

