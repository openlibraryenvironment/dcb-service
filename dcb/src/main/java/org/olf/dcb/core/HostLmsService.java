package org.olf.dcb.core;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.InvalidHostLmsConfigurationException;
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
	
	private final Map<String, String> idToCodeCache = new ConcurrentHashMap<>();
	
	public Mono<String> idToCode( UUID id ) {
		return Mono.justOrEmpty( Objects.toString(id, null) )
			.mapNotNull( idToCodeCache::get )
			.switchIfEmpty( Mono.from(hostLmsRepository.findById( id ))
				.map( lms -> {
					var theCode = lms.getCode();
					idToCodeCache.put(id.toString() , theCode);
					return theCode;
				}));
	}
	
	public Mono<DataHostLms> findById(UUID id) {
		return Mono.from(hostLmsRepository.findById(id))
			.doOnSuccess(hostLms -> log.debug("Found Host LMS: {}", hostLms))
			.switchIfEmpty(Mono.error(() -> new UnknownHostLmsException("ID", id)));
	}

	public Mono<DataHostLms> findByCode(String code) {
		// log.debug("findHostLmsByCode {}", code);

		return Mono.from(hostLmsRepository.findByCode(code))
			.switchIfEmpty(Mono.error(new UnknownHostLmsException("code", code)));
	}

	public Mono<HostLmsClient> getClientFor(final HostLms hostLms) {
		return Mono.justOrEmpty(hostLms.getClientType())
			// .doOnSuccess(type -> log.debug("Found client type: {}", type))
			.filter(HostLmsClient.class::isAssignableFrom)
			.switchIfEmpty(Mono.error(new InvalidHostLmsConfigurationException(
				hostLms.getCode(), "client class is either unknown or invalid")))
			.map(type -> context.createBean(type, hostLms))
			.cast(HostLmsClient.class);
	}

	public Mono<HostLmsClient> getClientFor(String code) {
		return findByCode(code)
			.flatMap(this::getClientFor);
	}

	public Mono<HostLmsClient> getClientFor(UUID id) {
		return findById(id)
			.flatMap(this::getClientFor);
	}

	public Mono<IngestSource> getIngestSourceFor(final HostLms hostLms) {
		final var ingestSource = hostLms.getIngestSourceType() != null
			? hostLms.getIngestSourceType()
			: hostLms.getClientType();

		return Mono.justOrEmpty(ingestSource)
			// .doOnSuccess(type -> log.debug("Found ingest source type: {} for {}", type, hostLms.getCode()))
			.filter(IngestSource.class::isAssignableFrom)
			.switchIfEmpty(Mono.error(new InvalidHostLmsConfigurationException( hostLms.getCode(), "ingest source class is either unknown or invalid")))
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
			.flatMap(this::getIngestSourceFor)
			.onErrorContinue(InvalidHostLmsConfigurationException.class,
				(error, source) -> log.warn("{}", error.getMessage()));
	}

	private Flux<DataHostLms> getAllHostLms() {
		// log.debug("getAllHostLms()");

		return Flux.from(hostLmsRepository.queryAll());
	}
}
