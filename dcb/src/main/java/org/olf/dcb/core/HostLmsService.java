package org.olf.dcb.core;

import java.util.UUID;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.IngestSourcesProvider;
import org.olf.dcb.storage.HostLmsRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class HostLmsService implements IngestSourcesProvider {
	private final BeanContext context;
	private final HostLmsRepository hostLmsRepository;

	HostLmsService(BeanContext context, HostLmsRepository hostLmsRepository) {
		this.hostLmsRepository = hostLmsRepository;
		this.context = context;
	}
	
	public Mono<DataHostLms> findById(UUID id) {
		return Mono.from(hostLmsRepository.findById(id))
			.switchIfEmpty(Mono.error(() -> new UnknownHostLmsException("ID", id)));
	}

	public Mono<DataHostLms> findByCode(String code) {
		log.debug("findHostLmsByCode {}", code);

		return Mono.from(hostLmsRepository.findByCode(code))
			.switchIfEmpty(Mono.error(new UnknownHostLmsException("code", code)));
	}

	public Mono<HostLmsClient> getClientFor(final HostLms hostLms) {
		return Mono.just(hostLms.getType())
			.filter(HostLmsClient.class::isAssignableFrom)
			.map(type -> context.createBean(type, hostLms))
			.cast(HostLmsClient.class);
	}

	public Mono<HostLmsClient> getClientFor(String code) {
		return findByCode(code)
			.flatMap(this::getClientFor);
	}
	
	public Mono<IngestSource> getIngestSourceFor(final HostLms hostLms) {
		return Mono.just(hostLms.getType())
			.filter(IngestSource.class::isAssignableFrom)
			.map(type -> context.createBean(type, hostLms))
			.cast(IngestSource.class);
	}

	public Mono<IngestSource> getIngestSourceFor(String code) {
		return findByCode(code)
			.flatMap(this::getIngestSourceFor);
	}

	@Override
	public Publisher<IngestSource> getIngestSources() {
		return getAllHostLms()
			.flatMap(this::getIngestSourceFor);
	}

	private Flux<DataHostLms> getAllHostLms() {
		log.debug("getAllHostLms()");

		return Flux.from(hostLmsRepository.queryAll());
	}

	public static class UnknownHostLmsException extends RuntimeException {
		UnknownHostLmsException(String propertyName, Object value) {
			super(String.format("No Host LMS found for %s: %s", propertyName, value));
		}
	}
}
