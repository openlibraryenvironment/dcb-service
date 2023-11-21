package org.olf.dcb.core;

import java.util.List;
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
		return getAllHostLms()
			.collectList()
			.doOnNext(list -> log.debug("Got list of hostLms systems {}", list))
			.map(list -> findFirstById(id, list));
	}

	private static DataHostLms findFirstById(UUID id, List<DataHostLms> list) {
		return list.stream()
			.filter(hostLms -> hostLms.getId().equals(id))
			.findFirst()
			.orElseThrow(() -> new UnknownHostLmsException("ID", id));
	}

	public Mono<DataHostLms> findByCode(String code) {
		log.debug("findHostLmsByCode {}", code);

		return getAllHostLms()
			.collectList()
			.map(list -> findFirstByCode(code, list))
			.switchIfEmpty(Mono.error(new UnknownHostLmsException("code", code)));
	}

	private static DataHostLms findFirstByCode(String code, List<DataHostLms> list) {
		return list.stream()
			.filter(hostLms -> hostLms.getCode().equals(code))
			.findFirst()
			.orElseThrow(() -> new UnknownHostLmsException("code", code));
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

	public Flux<DataHostLms> getAllHostLms() {
		log.debug("getAllHostLms()");

		return Flux.from(hostLmsRepository.queryAll());
	}

	@Override
	public Publisher<IngestSource> getIngestSources() {
		return getAllHostLms()
			.flatMap(this::getIngestSourceFor);
	}

	public static class UnknownHostLmsException extends RuntimeException {
		UnknownHostLmsException(String propertyName, Object value) {
			super(String.format("No Host LMS found for %s: %s", propertyName, value));
		}
	}
}
