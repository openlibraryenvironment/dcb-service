package org.olf.reshare.dcb.request.resolution.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.olf.reshare.dcb.request.resolution.SharedIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class FakeSharedIndexService implements SharedIndexService {
	static final Logger log = LoggerFactory.getLogger(FakeSharedIndexService.class);

	Map<UUID, ClusteredBib> sharedIndex = new HashMap<>();

	public void addClusteredBib(ClusteredBib clusteredBib) {
		log.debug(String.format("addBib(%s)", clusteredBib));

		sharedIndex.put(clusteredBib.id(), clusteredBib);
	}

	@Override
	public Mono<ClusteredBib> findClusteredBib(UUID uuid) {
		log.debug(String.format("findClusteredBib(%s)", uuid));

		return Mono.just(sharedIndex.get(uuid));
	}
}
