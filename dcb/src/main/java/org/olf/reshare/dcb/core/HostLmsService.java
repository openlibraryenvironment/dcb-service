package org.olf.reshare.dcb.core;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.ingest.IngestSourcesProvider;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class HostLmsService implements IngestSourcesProvider {
	// private final Map<UUID, HostLms> fromConfigById;
	private final BeanContext context;
	private final HostLmsRepository hostLmsRepository;

	HostLmsService(BeanContext context, HostLmsRepository hostLmsRepository) {
		this.hostLmsRepository = hostLmsRepository;
		this.context = context;
	}
	
	public Mono<DataHostLms> findById(UUID id) {
		return getAllHostLms()
			.collectList()
			.map(list -> findFirstById(id, list));
	}

	private static DataHostLms findFirstById(UUID id, List<DataHostLms> list) {
		return list.stream()
			.filter(hostLms -> hostLms.getId().equals(id))
			.findFirst()
			.orElseThrow(() -> new UnknownHostLmsException("ID", id));
	}

	public Mono<DataHostLms> findByCode(String code) {
		return getAllHostLms()
			.collectList()
			.map(list -> findFirstByCode(code, list));
	}

	private static DataHostLms findFirstByCode(String code, List<DataHostLms> list) {
		return list.stream()
			.filter(hostLms -> hostLms.getCode().equals(code))
			.findFirst()
			.orElseThrow(() -> new UnknownHostLmsException("code", code));
	}

	public Mono<HostLmsClient> getClientFor( final HostLms hostLms ) {
		return Mono.just( context.createBean(hostLms.getType(), hostLms) );
	}

	public Mono<HostLmsClient> getClientFor(String code) {
		return findByCode(code)
			.flatMap(this::getClientFor);
	}

	public Flux<DataHostLms> getAllHostLms() {
		return Flux.from(hostLmsRepository.findAll());
	}

	@Override
	public Publisher<IngestSource> getIngestSources() {
		return getAllHostLms()
			.filter( hlms -> IngestSource.class.isAssignableFrom( hlms.getType() ))
			.flatMap(this::getClientFor)
			.cast(IngestSource.class);
	}

	public static class UnknownHostLmsException extends RuntimeException {
		UnknownHostLmsException(String propertyName, Object value) {
			super(String.format("No Host LMS found for %s: %s", propertyName, value));
		}
	}
}
