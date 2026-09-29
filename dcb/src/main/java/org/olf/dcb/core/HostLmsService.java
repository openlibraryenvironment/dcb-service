package org.olf.dcb.core;

import static java.util.stream.Collectors.groupingBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.HostLmsProcessingStateCount;
import org.olf.dcb.core.model.InvalidHostLmsConfigurationException;
import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.IngestSourcesProvider;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.JobCheckpointRepository;
import org.olf.dcb.storage.SourceRecordRepository;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@Slf4j
@Singleton
public class HostLmsService implements IngestSourcesProvider {
	private static final Duration ALL_IMPORT_INGEST_DETAILS_TTL = Duration.ofMinutes(5);
	private static final int IMPORT_CHECKPOINT_LOOKUP_CONCURRENCY = 4;

	private final JsonNode EMPTY_JSON_NODE = JsonNode.createObjectNode(new HashMap<String, JsonNode>());
	
	private final BibRecordService bibRecordService;
	private final BeanContext context;
	private final HostLmsRepository hostLmsRepository;
	private final RawSourceRepository rawSourceRepo;
	private final SourceRecordRepository sourceRecordRepository;
	private final BeanProvider<SourceRecordService> sourceRecordServiceProvider;
	private final JobCheckpointRepository jobCheckpointRepository;
	
	private final BibRepository bibRepository;
	
	HostLmsService(
		BibRecordService bibRecordService,
		BeanContext context,
		HostLmsRepository hostLmsRepository,
		RawSourceRepository rawSourceRepo,
		SourceRecordRepository sourceRecordRepository,
		JobCheckpointRepository jobCheckpointRepository,
		BibRepository bibRepository,
		BeanProvider<SourceRecordService> sourceRecordServiceProvider
	) {
		this.bibRecordService = bibRecordService;
		this.context = context;
		this.hostLmsRepository = hostLmsRepository;
		this.rawSourceRepo = rawSourceRepo;
		this.sourceRecordRepository = sourceRecordRepository;
		this.sourceRecordServiceProvider = sourceRecordServiceProvider;
		this.jobCheckpointRepository = jobCheckpointRepository;
		this.bibRepository = bibRepository;
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
		if (hostLms instanceof DataHostLms dataHostLms
			&& dataHostLms.getIngestSourceClass() != null
			&& dataHostLms.getIngestSourceClass().isBlank()) {
			final var message = "ingest_source_class is blank";
			log.error("Skipping host LMS {}: {}", hostLms.getCode(), message);
			return Mono.error(new InvalidHostLmsConfigurationException(hostLms.getCode(), message));
		}

		final var configuredIngestSource = hostLms.getIngestSourceType();
		final var ingestSource = configuredIngestSource != null
			? configuredIngestSource
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
			// Contained per Host LMS, not by exception type: the constructor throws
			// IllegalArgumentException for absent client config, which Micronaut wraps as
			// BeanInstantiationException, and the old onErrorContinue matched neither - so
			// one misconfigured row 500d /info for every caller.
			.flatMap(hostLms -> getIngestSourceFor(hostLms)
				.onErrorResume(error -> {
					log.warn("Skipping ingest source for {} : {}",
						hostLms.getCode(), error.getMessage());

					return Mono.empty();
				}));
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
			.then( Mono.defer(() -> Mono.from(hostLmsRepository.delete(id))) )
			.thenReturn(id);
		
		// Need to fetch all bibs, soft delete them and then expunge the source records from the database.
		
	}

	/**
	 * Ingest and import details for one Host LMS. Counts only that Host LMS's rows, so the cost is
	 * bounded by its own catalogue rather than the consortium's.
	 *
	 * @return empty when there is no Host LMS with that id
	 */
	public Mono<Map<String, Object>> getImportIngestDetails(UUID id) {
		final Instant countedAt = Instant.now();

		return Mono.from(hostLmsRepository.findById(id))
			.flatMap(hostLms -> Mono.zip(
					Flux.from(sourceRecordRepository.getProcessingStateCountsForHostLms(id)).collectList(),
					Mono.from(bibRepository.getCountForHostLms(id)).defaultIfEmpty(0L))
				.flatMap(TupleUtils.function((stateCounts, bibRecordCount) ->
					importIngestDetailsFor(hostLms, stateCounts, bibRecordCount, countedAt))));
	}

	/**
	 * Ingest and import details for every Host LMS, served for up to
	 * {@link #ALL_IMPORT_INGEST_DETAILS_TTL} from one shared count.
	 */
	public Mono<List<Map<String, Object>>> getAllImportIngestDetails() {
		return allImportIngestDetails;
	}

	// Mono.cache does not cancel its source when a subscriber cancels (checked on reactor-core 3.8.5),
	// so a count that outlives a request timeout still completes and the retry is served from it
	// instead of starting a second scan of source_record and bib_record.
	private final Mono<List<Map<String, Object>>> allImportIngestDetails =
		Mono.defer(this::countAllImportIngestDetails)
			.cache(details -> ALL_IMPORT_INGEST_DETAILS_TTL, error -> Duration.ZERO, () -> Duration.ZERO);

	/**
	 * One grouped scan of each table, rather than three counts per Host LMS.
	 * Every map below is keyed by Host LMS, so it holds at most one entry per Host LMS
	 * (four per Host LMS for the processing states).
	 */
	protected Mono<List<Map<String, Object>>> countAllImportIngestDetails() {
		final Instant countedAt = Instant.now();

		return Mono.zip(
				Flux.from(sourceRecordRepository.getProcessingStateCountsByHostLms())
					.collect(groupingBy(HostLmsProcessingStateCount::getHostLmsId)),
				Flux.from(bibRepository.getIngestReport())
					.collectMap(RecordCountSummary::getSourceSystemId, RecordCountSummary::getRecordCount))
			.flatMap(TupleUtils.function((stateCountsByHostLms, bibCountsByHostLms) -> getAllHostLms()
				.flatMap(hostLms -> importIngestDetailsFor(hostLms,
						stateCountsByHostLms.getOrDefault(hostLms.getId(), List.of()),
						bibCountsByHostLms.getOrDefault(hostLms.getId(), 0L),
						countedAt),
					IMPORT_CHECKPOINT_LOOKUP_CONCURRENCY)
				.collectList()));
	}

	private Mono<Map<String, Object>> importIngestDetailsFor(DataHostLms hostLms,
		List<HostLmsProcessingStateCount> stateCounts, long bibRecordCount, Instant countedAt) {

		return importCheckpointFor(hostLms)
			.map(checkpoint -> {
				final Map<String, Object> details = new HashMap<>();
				details.put("id", hostLms.getId());
				details.put("name", hostLms.getName());
				details.put("errors", checkpoint.errors());
				if (checkpoint.ingestEnabled() != null) {
					details.put("ingestEnabled", checkpoint.ingestEnabled());
				}
				if (checkpoint.checkPointId() != null) {
					details.put("checkPointId", checkpoint.checkPointId());
					details.put("checkPoint", checkpoint.checkPoint());
				}
				details.put("processStates", stateCounts.stream()
					.map(stateCount -> new RecordCount(stateCount.getValue(), stateCount.getCount()))
					.toList());
				details.put("sourceRecordCount", stateCounts.stream()
					.mapToLong(HostLmsProcessingStateCount::getCount)
					.sum());
				details.put("bibRecordCount", bibRecordCount);
				details.put("countedAt", countedAt);
				return details;
			});
	}

	private Mono<ImportCheckpoint> importCheckpointFor(DataHostLms hostLms) {
		return getIngestSourceFor(hostLms)
			.flatMap(ingestSource -> ingestSource instanceof SourceRecordDataSource source
				? sourceRecordServiceProvider.get().createJobInstanceForSource(ingestSource, true)
					.flatMap(job -> findImportCheckpoint(job.getId())
						.map(checkPoint -> new ImportCheckpoint(
							sourceRecordServiceProvider.get().isIngestEnabled(source), job.getId(), checkPoint, List.of())))
				: Mono.<ImportCheckpoint>empty())
			.defaultIfEmpty(ImportCheckpoint.NONE)
			.onErrorResume(error -> {
				log.warn("Unable to read the import checkpoint for Host LMS [{}]", hostLms.getCode(), error);

				return Mono.just(ImportCheckpoint.failed("Unable to read the import checkpoint for id "
					+ hostLms.getId() + ", error: " + error.getMessage()));
			});
	}

	// The checkpoint repository requires a transaction; this one is scoped to a single-row read
	// so that the counts never share, and serialise on, its connection.
	@Transactional(readOnly = true)
	protected Mono<JsonNode> findImportCheckpoint(UUID jobId) {
		return Mono.from(jobCheckpointRepository.findCheckpointByJobId(jobId))
			.defaultIfEmpty(EMPTY_JSON_NODE);
	}

	private record ImportCheckpoint(Boolean ingestEnabled, UUID checkPointId, JsonNode checkPoint,
		List<String> errors) {

		static final ImportCheckpoint NONE = new ImportCheckpoint(null, null, null, List.of());

		static ImportCheckpoint failed(String error) {
			return new ImportCheckpoint(null, null, null, List.of(error));
		}
	}
}
