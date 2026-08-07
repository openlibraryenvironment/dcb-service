package org.olf.dcb.dataimport.job;

import static services.k_int.utils.ReactorUtils.withFluxLogging;
import static services.k_int.utils.ReactorUtils.withMonoLogging;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.storage.SourceRecordRepository;
import org.slf4j.event.Level;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.jobs.JobChunk;
import services.k_int.jobs.JobChunkProcessor;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;
import services.k_int.jobs.ReactiveJobRunnerService;
import services.k_int.micronaut.concurrency.ConcurrencyGroupService;
import services.k_int.micronaut.scheduling.processor.AppTask;


@Slf4j
@Singleton
@ExecuteOn(TaskExecutors.BLOCKING)
@ApplicableChunkTypes( SourceRecordImportChunk.class )
public class SourceRecordService implements JobChunkProcessor, ApplicationEventListener<RefreshEvent>, EntityEventListener<DataHostLms> {

	private final HostLmsService lmsService;
	private final SourceRecordRepository sourceRecords;
	private final ReactiveJobRunnerService jobService;
  private final ConcurrencyGroupService concurrency;
  private final ReactorFederatedLockService lockService;

	public SourceRecordService(HostLmsService lmsService, SourceRecordRepository sourceRecords, ReactiveJobRunnerService jobService, ConcurrencyGroupService concurrency, ReactorFederatedLockService lockService) {
		log.info("SourceRecordService::init");
		this.lmsService = lmsService;
		this.sourceRecords = sourceRecords;
		this.jobService = jobService;
		this.concurrency = concurrency;
		this.lockService = lockService;
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<SourceRecord> getByLocalId(UUID id) {
		return Mono.from( sourceRecords.getById(id) );
	}
	
	public Mono<SourceRecordImportJob> createJobInstanceForSource( IngestSource ingestSource ) {
		return(createJobInstanceForSource(ingestSource, false));
	}

	public boolean isIngestEnabled(SourceRecordDataSource sourceRecordDataSource) {
		return(sourceRecordDataSource.isSourceImportEnabled());
	}
	
	public Mono<SourceRecordImportJob> createJobInstanceForSource(
		IngestSource ingestSource,
		boolean ignoreEnabled
	) {
		if (!SourceRecordDataSource.class.isAssignableFrom(ingestSource.getClass())) {
			log.error("Ingest source [{}] does not implement [{}]", SourceRecordDataSource.class);
			return Mono.empty();
		}

		SourceRecordDataSource source = (SourceRecordDataSource) ingestSource;
		if (!ignoreEnabled && !isIngestEnabled(source)) {
			log.info("Source record import is explicitly disabled for [{}]", source.getName());
			return Mono.empty();
		}

		return Mono.just(source)
			.map(SourceRecordImportJob::new);
	}
	
	@Transactional(readOnly = true)
	protected Flux<SourceRecordImportJob> getSourceRecordDataSources() {
		return Flux.from(lmsService.getIngestSources())
			.sort( (s1, s2) -> Optional.ofNullable(s1)
				.map(IngestSource::getName)
				.flatMap( name1 -> Optional.ofNullable(s2)
					.map(IngestSource::getName)
					.map( name2 -> name1.compareTo(name2) ))
				.orElse( s1 != null ? 1 : -1 ))
				.transform(
						concurrency.toGroupedSubscription(this::createJobInstanceForSource));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Mono<JobChunk<T>> processChunk(final JobChunk<T> chunk) {
		
		if (SourceRecordImportChunk.class.isAssignableFrom(chunk.getClass())) {
			return processSourceRecordImportChunk((SourceRecordImportChunk) chunk)
				.thenReturn(chunk);
		}

		return Mono.error(new IllegalArgumentException("Unsupported Chunk type %s".formatted(chunk.getClass())));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<SourceRecordImportChunk> processSourceRecordImportChunk(final SourceRecordImportChunk chunk) {

		try {
			return Flux.fromIterable(chunk.getData())
					.flatMap(this::save)
					.then(Mono.just(chunk))
					.transform( withMonoLogging(log, l -> 
						l.doOnSuccess(Level.TRACE, processedChunk -> log.info("Processed chunk of [{}] items", processedChunk.getSize()))));
		} catch (Exception e) {
			return Mono.error(e);
		}
	}
	
	// Reconciliation can emit far more records than a harvest chunk, so it is committed in bounded
	// batches rather than one transaction spanning the whole sweep.
	private static final int RECONCILE_BATCH_SIZE = 100;

	// A healthy source writes a checkpoint on every chunk, and the import job runs every two
	// minutes, so half an hour without one means the job is not progressing.
	private static final Duration CHECKPOINT_STALL_THRESHOLD = Duration.ofMinutes(30);

	/**
	 * Detect source imports that have stopped writing checkpoints and ask the source to correct
	 * itself.
	 *
	 * A run that produces no chunk ends without saving a checkpoint, which is indistinguishable from
	 * success in the logs. checkpointDate is therefore the only progress signal available, and
	 * because it lives inside the source's own checkpoint JSON only the source can read it - hence
	 * nudgeStalledCheckpoint rather than logic here.
	 *
	 * Nudges are deliberately conservative. Anything a nudge cannot fix is alarmed and left for an
	 * operator, because the only stronger remedy is a full re-harvest and that must never fire
	 * automatically across a fleet of library systems.
	 */
	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "5m", fixedDelay = "15m")
	public void stalledCheckpointWatchdog() {

		log.debug("Checking source import checkpoints for stalls");

		getSourceRecordDataSources()
			.concatMap(this::nudgeIfStalled)
			.transformDeferred(lockService.withLockOrEmpty("checkpoint-watchdog"))
			.subscribe(
				jobId -> log.info("Nudged stalled checkpoint for job [{}]", jobId),
				error -> log.error("Checkpoint stall watchdog failed", error));
	}

	private Mono<UUID> nudgeIfStalled( SourceRecordImportJob job ) {

		final var jobId = job.getId();

		return jobService.findCheckpoint(jobId)
			.flatMap( checkpoint -> Mono.justOrEmpty(
				job.getDatasource().nudgeStalledCheckpoint(checkpoint, CHECKPOINT_STALL_THRESHOLD)) )
			.flatMap( nudged -> jobService.replaceCheckpoint(jobId, nudged) )
			.thenReturn(jobId)
			.onErrorResume( error -> {
				// One source failing must not stop the watchdog reaching the others.
				log.error("Could not evaluate checkpoint for job [{}]", jobId, error);
				return Mono.empty();
			});
	}

	/**
	 * Fetch and store the records a source holds but DCB does not.
	 *
	 * This is the counterpart to fixing a stalled harvest: resuming correctly stops the bleeding,
	 * but records skipped earlier stay invisible because delta harvesting only surfaces changes
	 * after the watermark. Sources that cannot enumerate themselves return nothing and this is a
	 * no-op for them.
	 */
	public Mono<Long> reconcileSource( SourceRecordDataSource datasource, UUID hostLmsId ) {

		log.info("Starting reconciliation sweep for [{}]", datasource.getName());

		return datasource.findMissingRecords( sourceRecords.findRemoteIdsByHostLmsId(hostLmsId) )
			.buffer(RECONCILE_BATCH_SIZE)
			.concatMap(this::saveReconciledBatch)
			.reduce(0L, Long::sum)
			.doOnSuccess( count -> log.info("Reconciliation for [{}] stored {} previously missing records",
				datasource.getName(), count) )
			.doOnError( e -> log.error("Reconciliation for [{}] failed", datasource.getName(), e) );
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<Long> saveReconciledBatch( List<SourceRecord> batch ) {
		return Flux.fromIterable(batch)
			.concatMap(this::save)
			.count();
	}

	// Single flight, across the whole estate rather than per host. A sweep issues thousands of
	// sequential requests at one library's server, and this is a rare break-glass operation, so
	// serialising it is the dumbest thing that is obviously safe - and it means no guard keyed on
	// a host code, which would be a dynamic map key for no benefit.
	// volatile because the check outside the synchronized block must see another thread's write.
	private volatile Mono<String> reconciliation;

	// Bounded: fixed keys, written by the sweep and read by the admin API. Never keyed by record.
	private final Map<String, Object> reconcileStatusReport = new ConcurrentHashMap<>();

	/**
	 * Kick off a reconciliation sweep and return as soon as it has started.
	 *
	 * The sweep itself is unbounded in wall clock time - a large catalogue with wide damage is
	 * thousands of sequential requests - so it must never be awaited by an HTTP request. The caller
	 * gets an acknowledgement; progress is read from {@link #getReconcileStatus()}.
	 */
	public Mono<String> startReconciliation( String hostLmsCode ) {

		if (reconciliation == null) {
			synchronized (this) {
				if (reconciliation == null) {

					reconcileStatusReport.put("status", "Running");
					reconcileStatusReport.put("hostLmsCode", hostLmsCode);
					reconcileStatusReport.put("startTime", Instant.now().toString());
					reconcileStatusReport.remove("recordsRecovered");
					reconcileStatusReport.remove("lastError");

					reconciliation = Mono.<String>create(report -> {
						log.info("Starting reconciliation sweep for [{}]", hostLmsCode);
						report.success("Reconciliation for %s started at [%s]".formatted(hostLmsCode, Instant.now()));

						resolveDataSource(hostLmsCode)
							.flatMap(TupleUtils.function(this::reconcileSource))
							.doOnSuccess(count -> reconcileStatusReport.put("recordsRecovered", count))
							.doOnError(error -> reconcileStatusReport.put("lastError", String.valueOf(error.getMessage())))
							.doOnTerminate(() -> {
								reconciliation = null;
								reconcileStatusReport.put("status", "Not Active");
								reconcileStatusReport.put("endTime", Instant.now().toString());
							})
							.subscribe(
								count -> log.info("Reconciliation for [{}] recovered {} records", hostLmsCode, count),
								error -> log.error("Reconciliation for [{}] failed", hostLmsCode, error));

					}).cache();
				}
			}
		}
		else {
			log.info("Reconciliation already running. NOOP");
		}

		return reconciliation;
	}

	public Map<String, Object> getReconcileStatus() {
		return reconcileStatusReport;
	}

	/**
	 * Clear a source import checkpoint so the next scheduled run starts from the beginning.
	 *
	 * The heavy remedy - it re-walks the entire catalogue of the target system - so it stays
	 * operator triggered and never automatic. Prefer reconciliation when the damage is partial.
	 * Fast enough to answer synchronously; it deletes one row.
	 */
	public Mono<String> resetCheckpointFor( String hostLmsCode ) {

		return lmsService.getIngestSourceFor(hostLmsCode)
			.flatMap(ingestSource -> createJobInstanceForSource(ingestSource, true))
			.flatMap(job -> jobService.resetJob(job.getId())
				.thenReturn("Checkpoint cleared for %s. The next scheduled run will start a full harvest."
					.formatted(hostLmsCode)))
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				"No source record import job could be resolved for %s".formatted(hostLmsCode))));
	}

	private Mono<Tuple2<SourceRecordDataSource, UUID>> resolveDataSource( String hostLmsCode ) {

		return lmsService.findByCode(hostLmsCode)
			.flatMap(hostLms -> lmsService.getIngestSourceFor(hostLms)
				.filter(SourceRecordDataSource.class::isInstance)
				.cast(SourceRecordDataSource.class)
				.map(datasource -> Tuples.of(datasource, hostLms.getId())))
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				"No source record data source could be resolved for %s".formatted(hostLmsCode))));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<SourceRecord> save ( SourceRecord srcRec ) {
		return Mono.from(sourceRecords.saveOrUpdate(srcRec))
				.transform( withMonoLogging(log, l -> 
					l.doOnSuccess(Level.TRACE, savedRecord -> log.trace("Save source record [{}]", srcRec))));
	}

  // II: I'm trying to work out why in my clone of Mob live I see the number of records requiring processing
  // is (correctly, due to outdated versio) going up, but those records are never being processed. I can't see
  // this method called anywhere.. This is note to self as I try to unpick whats going so wrong.
  // call possibly comes from IngestJob
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<List<SourceRecord>> getUnprocessedRecords (@NonNull Pageable page) {
		
		return Flux.from(sourceRecords.findAllByProcessingState(ProcessingStatus.PROCESSING_REQUIRED, page))
				.transform(	withFluxLogging(log, l -> 
					l.doOnSubscribe(Level.DEBUG, _s -> log.debug("Fetching page of SourceRecord data [{}]", page))))
				.collectList();
	}
	
  // II: I'm trying to work out why in my clone of Mob live I see the number of records requiring processing
  // is (correctly, due to outdated versio) going up, but those records are never being processed. I can't see
  // this method called anywhere.. This is note to self as I try to unpick whats going so wrong.
  // call possibly comes from IngestJob
	@Transactional(propagation = Propagation.MANDATORY)
	public @NonNull Mono<UUID> requireProcessing (@NonNull UUID sourceRecordId) {
		return Mono.from(sourceRecords.updateProcessingStateById(sourceRecordId, ProcessingStatus.PROCESSING_REQUIRED))
				.transform(	withMonoLogging(log, l ->
					l.doOnSubscribe(Level.DEBUG, _s -> log.debug("Flagging source record [{}] for reprocessing", sourceRecordId))))
				.thenReturn(sourceRecordId);
	}
	
	// Technically because we are using LIKE here we "could" find more than one. Handle that outside of this method if
	// we want to fail when that is the case
	public @NonNull Flux<SourceRecord> findByHostLmsIdAndRemoteIdLike(@NotNull UUID sourceSystemId, @NonNull String sourceRecordId) {
		return Flux.from( sourceRecords.findByHostLmsIdAndRemoteIdLike(sourceSystemId, "%" + sourceRecordId) );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Void> updateProcessingInformation(
			@NonNull UUID id,
			@NonNull Instant lastProcessed,
			@NonNull ProcessingStatus processingState,
			@Nullable String processingInformation ) {
		
		return Mono.from(sourceRecords.updateById(id, lastProcessed, processingState, processingInformation))
				// Sanity check.
				.flatMap(count -> {
					if (count != 1) return Mono.error(
							new IllegalStateException("Source record processing infromation update returned %d result. Expected exactly 1 record update".formatted(count)));
					
					return Mono.empty();
				});
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Void> updateProcessingInformation(
			@NonNull UUID id,
			@NonNull Instant lastProcessed,
			@NonNull ProcessingStatus processingState ) {
		return updateProcessingInformation(id, lastProcessed, processingState, null);
	}
	
	private void jobSubscriber( long time, long count ) {
		Duration elapsed = Duration.ofMillis(time);
		log.info("Finsihed adding {} records. Total time {} hours, {} minute and {} seconds", count,
				elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
	}
	
	private void errorSubscriber ( Throwable t ) {
		log.error("Error during import job", t);
	}
	
	private Flux<JobChunk<SourceRecord>> processSingleJob( SourceRecordImportJob job ) {
		return Flux.just( job )
			.flatMap( jobService::processJobInstance )
			.takeUntil( chunk -> {
				
				// Take until will make this chunk the last chunk, but still emit it.
				if (interruption.isEmpty()) return false;
				
				log.info( "Gracefully interrupting job {}. Cause: {}", job.getName(), interruption.get() );
				
				return true;
			})
			.onErrorResume( err -> {
				log.atError()
					.setCause(err)
					.log("Terminating job {} because of Error", job.getName());
				return Mono.empty();
			})
		;
	}
	
	private long getDataCountForChunk(JobChunk<SourceRecord> chunk) {
		return Optional.ofNullable(chunk.getData()) // Extract resource count.
			.map( Collection::size )
			.map( Integer::longValue )
			.orElse(0L);
	}

	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "20s", fixedDelay = "2m")
	public void scheduleSourceRecordJob() {

    log.info("Attempting to schedule source record job");
		
		// Empty interrupts before we start.
		interruption = Optional.empty();
		
		getSourceRecordDataSources()
			.flatMap( this::processSingleJob )
			.map( this::getDataCountForChunk )
			.reduce( Long::sum )
			.elapsed()
			.transformDeferred(lockService.withLockOrEmpty("import-job"))
			.subscribe(
					TupleUtils.consumer(this::jobSubscriber),
					this::errorSubscriber);
	}
	

	private Optional<String> interruption = Optional.empty();
	
	private void generateInterrupt( String reason ) {
		interruption = Optional.of( reason );
	}
	
	@Override
	public void onApplicationEvent(RefreshEvent event) {
		generateInterrupt( "Refresh event" );
	}
	
	@Override
	public void postPersist(@NonNull EntityEventContext<DataHostLms> context) {
		generateInterrupt( "HostLms [%s] added".formatted(context.getEntity().name) );
	}
	
	@Override
	public void postUpdate(@NonNull EntityEventContext<DataHostLms> context) {
		generateInterrupt( "HostLms [%s] updated".formatted(context.getEntity().name) );
	}
	
	@Override
	public void postRemove(@NonNull EntityEventContext<DataHostLms> context) {
		generateInterrupt( "HostLms [%s] removed".formatted(context.getEntity().name) );
	}
}
