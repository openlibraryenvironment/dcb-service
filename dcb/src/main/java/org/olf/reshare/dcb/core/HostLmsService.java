package org.olf.reshare.dcb.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
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
	
	private final Map<UUID, HostLms> fromConfigById;
	private final Map<String, HostLms> fromConfigByCode;
	private final BeanContext context;
	private final HostLmsRepository hostLmsRepository;

	HostLmsService(HostLms[] confHosts, BeanContext context,
                       HostLmsRepository hostLmsRepository) {
		this.hostLmsRepository = hostLmsRepository;
		this.context = context;
		this.fromConfigById = Stream.of(confHosts)
			.collect(Collectors.toUnmodifiableMap(HostLms::getId, item -> item));
		this.fromConfigByCode = Stream.of(confHosts)
			.collect(Collectors.toUnmodifiableMap(HostLms::getCode, item -> item));
	}
	
	public Mono<HostLms> findById( UUID id ) {
		return Mono.justOrEmpty(fromConfigById.get(id));
	}

	public Mono<HostLms> findByCode( String code ) {
		return getAllHostLms()
			.collectList()
			.map(list -> findFirstByCode(code, list));
	}

	private static HostLms findFirstByCode(String code, List<HostLms> list) {
		return list.stream()
			.filter(hostLms -> hostLms.getCode().equals(code))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No host found for code: " + code));
	}

	public Mono<HostLmsClient> getClientFor( final HostLms hostLms ) {
		return Mono.just( context.createBean(hostLms.getType(), hostLms) );
	}

	public Mono<HostLmsClient> getClientFor(String code) {
		return findByCode(code)
			.flatMap(this::getClientFor);
	}

	public Flux<HostLms> getAllHostLms() {
		return Flux.merge(Flux.fromIterable( fromConfigById.values() ), hostLmsRepository.findAll());
	}

	@Override
	public Publisher<IngestSource> getIngestSources() {
		return getAllHostLms()
			.filter( hlms -> IngestSource.class.isAssignableFrom( hlms.getType() ))
			.flatMap(this::getClientFor)
			.cast(IngestSource.class);
	}
}
