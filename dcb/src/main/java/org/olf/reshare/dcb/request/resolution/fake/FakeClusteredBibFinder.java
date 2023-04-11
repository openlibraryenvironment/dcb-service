package org.olf.reshare.dcb.request.resolution.fake;

import static java.util.UUID.randomUUID;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.olf.reshare.dcb.request.resolution.ClusteredBibFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.Data;
import reactor.core.publisher.Mono;

@Singleton
public class FakeClusteredBibFinder implements ClusteredBibFinder {
	private static final Logger log = LoggerFactory.getLogger(FakeClusteredBibFinder.class);

	@Override
	public Mono<ClusteredBib> findClusteredBib(UUID bibClusterId) {
		log.debug("findClusteredBib({})", bibClusterId);

		return Mono.just(ClusteredBib.builder()
			.id(bibClusterId)
			.title("fakeTitle")
			.bibs(List.of(createFakeBib(), createFakeBib(), createFakeBib()))
			.build());
	}

	private static Bib createFakeBib() {
		return new Bib(randomUUID(), "FAKE_BIB_RECORD_ID",
			new FakeHostLms(randomUUID(), "FAKE_HOST_LMS_CODE", "Fake Host LMS",
				SierraLmsClient.class, Map.of()));
	}

	@Data
	private static class FakeHostLms implements HostLms {
		private final UUID Id;
		private final String code;
		private final String name;
		private final Class<? extends HostLmsClient> type;
		private final Map<String, Object> clientConfig;
	}
}
