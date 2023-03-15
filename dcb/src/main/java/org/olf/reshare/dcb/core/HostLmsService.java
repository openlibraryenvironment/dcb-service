package org.olf.reshare.dcb.core;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.ingest.IngestSourcesProvider;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.olf.reshare.dcb.storage.HostLmsRepository;

@Singleton
public class HostLmsService implements IngestSourcesProvider {
	
	private final Map<UUID, HostLms> fromConfigById;
	private final BeanContext context;
        private final HostLmsRepository hostLmsRepository;
	
	HostLmsService(HostLms[] confHosts, 
                       BeanContext context,
                       HostLmsRepository hostLmsRepository) {
		this.context = context;
                this.hostLmsRepository = hostLmsRepository;
		this.fromConfigById = Stream.of(confHosts)
			.collect(Collectors.toUnmodifiableMap(HostLms::getId, item -> item));
	}
	
	public Mono<HostLms> findById( UUID id ) {
		return Mono.justOrEmpty( fromConfigById.get(id) );
	}
	
	public Mono<HostLmsClient> getClientFor( final HostLms hostLms ) {
		return Mono.just( context.createBean(hostLms.getType(), hostLms) );
	}
	
	public <T> Mono<T> withHostLmsClient( final HostLms hostLms, Function<HostLmsClient, T> function ) {
		return getClientFor( hostLms )
			.map(function);
	}
	
	public Mono<HostLmsClient> withHostLmsClient( final HostLms hostLms, Consumer<HostLmsClient> consumer ) {
		return withHostLmsClient(hostLms, client -> {
			consumer.accept(client);
			return client;
		});
	}
	
	public Flux<HostLms> getAllHostLms() {
		// return Flux.fromIterable( fromConfigById.values() );
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
