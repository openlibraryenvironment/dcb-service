package org.olf.reshare.dcb.request.resolution.fake;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
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
		log.debug("findClusteredBib({})", bibClusterId);

		return Mono.just(new ClusteredBib(bibClusterId,
				List.of(createFakeBib(), createFakeBib(), createFakeBib())));
	}

	private static Bib createFakeBib(){
		return new Bib(UUID.randomUUID(), "FAKE_BIB_RECORD_ID", "FAKE_HOST_LMS_CODE");
	}
}
