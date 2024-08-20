package org.olf.dcb.dataimport.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.storage.SourceRecordRepository;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
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
@ExecuteOn(TaskExecutors.BLOCKING)
@ApplicableChunkTypes( SourceRecordImportChunk.class )
public class SourceRecordService implements JobChunkProcessor {

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

	private Mono<SourceRecordImportJob> createJobInstanceForSource( IngestSource ingestSource ) {
		if (!SourceRecordDataSource.class.isAssignableFrom(ingestSource.getClass())) {
			log.error("Ingest source [{}] does not implement [{}]", SourceRecordDataSource.class);
			return Mono.empty();
		}

		SourceRecordDataSource source = (SourceRecordDataSource) ingestSource;
		if (!source.isSourceImportEnabled()) {
			log.info("Source record import is explicitly disabled for [{}]", source.getName());
			return Mono.empty();
		}

		return Mono.just(source)
			.map(SourceRecordImportJob::new);
	}
	
	@Transactional(readOnly = true)
	protected Flux<SourceRecordImportJob> getSourceRecordDataSources() {
		return Flux.from(lmsService.getIngestSources())
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
						.doOnSuccess(processedChunk -> log.info("Processed chunk of [{}] items", processedChunk.getSize()));
		} catch (Exception e) {
			return Mono.error(e);
		}
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<SourceRecord> save ( SourceRecord srcRec ) {
		return Mono.from(sourceRecords.saveOrUpdate(srcRec))
				.doOnSuccess(savedRecord -> log.trace("Save source record [{}]", srcRec));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Page<SourceRecord>> getUnprocessedRecords (@NonNull Pageable page) {
		return Mono.from(sourceRecords.findAllByLastProcessedIsNullOrProcessingState(ProcessingStatus.PROCESSING_REQUIRED, page))
				.doOnSubscribe(_s -> log.debug("Fetching page of SourceRecord data [{}]", page));
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

	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "20s")
	protected void scheduleSourceRecordJob() {
		getSourceRecordDataSources()
			.flatMap( jobService::processJobInstance )
			.map(chunk -> Optional.ofNullable(chunk.getData()) // Extract resource count.
					.map( Collection::size )
					.map( Integer::longValue )
					.orElse(0L))
			.reduce( Long::sum )
			.elapsed()
			.transformDeferred(lockService.withLockOrEmpty("import-job"))
			.subscribe(
					TupleUtils.consumer(this::jobSubscriber),
					this::errorSubscriber);
	}
}
