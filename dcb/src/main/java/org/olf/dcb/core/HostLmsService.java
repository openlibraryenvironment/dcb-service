package org.olf.dcb.core;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.InvalidHostLmsConfigurationException;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.IngestSourcesProvider;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.SourceRecordRepository;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class HostLmsService implements IngestSourcesProvider {
	public static final String BASE_URL = "base-url";
	
	private final BibRecordService bibRecordService;
	private final BeanContext context;
	private final HostLmsRepository hostLmsRepository;
	private final RawSourceRepository rawSourceRepo;
	private final SourceRecordRepository sourceRecordRepository;

	HostLmsService(
		BibRecordService bibRecordService,
		BeanContext context,
		HostLmsRepository hostLmsRepository,
		RawSourceRepository rawSourceRepo,
		SourceRecordRepository sourceRecordRepository
	) {
		this.bibRecordService = bibRecordService;
		this.context = context;
		this.hostLmsRepository = hostLmsRepository;
		this.rawSourceRepo = rawSourceRepo;
		this.sourceRecordRepository = sourceRecordRepository;
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
			.cast(IngestSource.class)
      .doOnError(e -> {
        log.error("Error creating ingest source for {} : {}",  hostLms.getCode(), e.getMessage());
      });
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

	protected Flux<DataHostLms> getAllHostLms() {
		// log.debug("getAllHostLms()");

		return Flux.from(hostLmsRepository.queryAll());
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Long> deleteAllHostLmsBibs ( UUID owner ) {
		
		final int reportChunkSize = 1_000;
		final Pageable page = Pageable.from(0, reportChunkSize);
		
		return Flux.just( page )
			.repeat()
			.concatMap( p -> Mono.from( bibRecordService.getPageOfHostLmsBibs( owner, p ) )
					.flatMap(this::deleteChunkOfBibs) )
			.takeWhile( deleted -> deleted > 0 )
			.reduce(0L, (total, removed) -> {
				log.info("Removed chunk of [{}] bibs for host lms [{}]", removed, owner);
				return total + removed;
			})
			.doOnSuccess( total -> log.info("Removed [{}] bibs in total for host lms [{}]", total, owner) );
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<Long> deleteChunkOfBibs(Iterable<BibRecord> chunk) {
		
		return Flux.fromIterable( chunk )
			.concatMap( bib -> bibRecordService.deleteBibAndUpdateCluster(bib).thenReturn(bib) )
			.count();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Integer> deleteAllSourceRecords (UUID hostId) {
		log.info("Delete all source records for host lms [{}]", hostId);
		return Mono.from(sourceRecordRepository.deleteAllByHostLmsId(hostId))
			.doOnSuccess( count -> log.info("Removed [{}] source records for HostLms [{}]", count, hostId) );
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Integer> deleteAllRawSourceRecords (UUID hostId) {
		log.info("Delete all raw source records for host lms [{}]", hostId);
		return Mono.from(rawSourceRepo.deleteAllByHostLmsId(hostId))
			.doOnSuccess( count -> log.info("Removed [{}] RAW source records for HostLms [{}]", count, hostId) );
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<UUID> deleteHostLmsData( @NonNull DataHostLms lms ) {

		final UUID id = lms.getId();

    // We should set enabled = false in the host LMS config to disable future ingests
    Map<String,Object> cc = lms.getClientConfig();
    if ( cc != null ) {
      cc.put("ingest",Boolean.FALSE);
    }

		return Mono.from(hostLmsRepository.update(lms))
      .then( Mono.defer(() -> deleteAllHostLmsBibs( id )))
			.then( Mono.defer(() -> deleteAllRawSourceRecords(id)) )
			.then( Mono.defer(() -> deleteAllSourceRecords(id)) )
			.thenReturn(id);
		
		// Need to fetch all bibs, soft delete them and then expunge the source records from the database.
		
	}

	/**
	 * Retrieves the base URL configuration for a given host LMS code.
	 *
	 * @param hostLmsCode the code identifying the host LMS
	 * @return Mono containing the base URL, or null if not found
	 */
	public Mono<String> getHostLmsBaseUrl(String hostLmsCode) {
		return getClientFor(hostLmsCode)
			.map(HostLmsClient::getConfig)
			.map(config -> (String) config.get(BASE_URL));
	}
}
