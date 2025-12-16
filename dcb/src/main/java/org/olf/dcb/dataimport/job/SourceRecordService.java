package org.olf.dcb.dataimport.job;

import static services.k_int.utils.ReactorUtils.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import io.micronaut.data.model.Page;
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
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<SourceRecord> save ( SourceRecord srcRec ) {
		return Mono.from(sourceRecords.saveOrUpdate(srcRec))
				.transform( withMonoLogging(log, l -> 
					l.doOnSuccess(Level.TRACE, savedRecord -> log.trace("Save source record [{}]", srcRec))));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<List<SourceRecord>> getUnprocessedRecords (@NonNull Pageable page) {
		
		return Flux.from(sourceRecords.findAllByProcessingState(ProcessingStatus.PROCESSING_REQUIRED, page))
				.transform(	withFluxLogging(log, l -> 
					l.doOnSubscribe(Level.DEBUG, _s -> log.debug("Fetching page of SourceRecord data [{}]", page))))
				.collectList();
	}
	
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
	protected void scheduleSourceRecordJob() {

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
	
	private void generateInterrupt( @NonNull String reason ) {
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
