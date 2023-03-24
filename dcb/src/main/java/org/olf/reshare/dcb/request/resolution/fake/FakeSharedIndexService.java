package org.olf.reshare.dcb.request.resolution.fake;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.request.resolution.Agency;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.olf.reshare.dcb.request.resolution.Holdings;
import org.olf.reshare.dcb.request.resolution.Item;
import org.olf.reshare.dcb.request.resolution.SharedIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class FakeSharedIndexService implements SharedIndexService {
	private static final Logger log = LoggerFactory.getLogger(FakeSharedIndexService.class);

	@Override
	public Mono<ClusteredBib> findClusteredBib(UUID bibClusterId) {
		log.debug(String.format("findClusteredBib(%s)", bibClusterId));

		return Mono.just(new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Agency("fake agency"),
				List.of(new Item(UUID.randomUUID()))))));
	}
}
