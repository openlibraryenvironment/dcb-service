package org.olf.dcb.ingest.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.ingest.conversion.SourceToIngestRecordConverter;
import org.olf.dcb.ingest.job.IngestJobChunk.IngestJobChunkBuilder;
import org.olf.dcb.ingest.job.IngestOperation.IngestOperationBuilder;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.jobs.Job;
import services.k_int.jobs.JobChunk;
import services.k_int.jobs.JobChunkProcessor;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;
import services.k_int.jobs.ReactiveJobRunnerService;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Slf4j
@Singleton
@Replaces( IngestService.class )
@ApplicableChunkTypes( IngestJobChunk.class )
public class IngestJob implements Job<IngestOperation>, JobChunkProcessor {
	private static final String JOB_NAME = "Data ingest job";
	
	private final int PAGE_SIZE = 250;
	private final Map<String, Mono<SourceToIngestRecordConverter>> sourceRecConverterCache = new ConcurrentHashMap<>();
	
	
	private final ConversionService conversionService;
	private final SourceRecordService sourceRecordService;
	private final ReactiveJobRunnerService jobRunnerService;
	private final HostLmsService hostLmsService;
	private final BibRecordService bibRecordService;
	private final RecordClusteringService recordClusteringService;
  private final ReactorFederatedLockService lockService;
	
	public IngestJob(SourceRecordService sourceRecordService, ConversionService conversionService, ReactiveJobRunnerService jobRunnerService, HostLmsService hostLmsService, RecordClusteringService recordClusteringService, BibRecordService bibRecordService, ReactorFederatedLockService lockService) {
		this.conversionService = conversionService;
		this.sourceRecordService = sourceRecordService;
		this.jobRunnerService = jobRunnerService;
		this.hostLmsService = hostLmsService;
		this.bibRecordService = bibRecordService;
		this.recordClusteringService = recordClusteringService;
		this.lockService = lockService;
		
		log.info("Ingest job is active");
	}
	
	@NonNull
	protected Mono<IngestJobChunk> buildChunkFromPage( @NonNull Page<SourceRecord> page ) {
		
		// NOTE: This job doesn't need a pointer for resumption. It always fetches the first page of results that haven't been
		// processed. As the results are processed that should slide processed results off the page. The transactional
		// nature of the job runner should also assert that this is sequential for us, meaning we don't need to keep a
		// Cursory checkpoint token. We create one here for information only.
		
		var params = IngestJobParams.builder()
			.lastFetchTime( Instant.now() )
			.pageSize(PAGE_SIZE)
			.build();
		
		JsonNode newCheckpoint = conversionService.convertRequired( params, JsonNode.class);
		
		boolean terminal = page.getNumberOfElements() < page.getSize();
		
		IngestJobChunkBuilder chunk = IngestJobChunk.builder()
			.jobId( getId() )
			.lastChunk( terminal )
			.checkpoint( newCheckpoint );
		
		return Flux.fromIterable( page )
			.flatMap( this::createOperation )
			.collectList()
			.map( chunk::data )
			.map( IngestJobChunkBuilder::build );
	}
	
	protected Mono<IngestOperation> createOperation( SourceRecord source ) {
		IngestOperationBuilder op = IngestOperation.builder()
			.sourceId( source.getId() );
		
		return Mono.from( tryConvertToIngestRecord(source) )
			.map( ingestRecord -> { 
				var newBuilder = op.ingest(ingestRecord);
				
				return newBuilder;
			})
			.onErrorResume(err -> {
				log.warn("Error creating IngestRecord from source [{}]", source);
				
				// Just preserve the builder with no record
				return Mono.just(op);
			})
			.defaultIfEmpty(op) // Just preserve the builder with no record
			.map( IngestOperationBuilder::build );
	}
	
	// Wrap for proper transactional boundaries.
	@Transactional(readOnly = true)
	protected Mono<Page<SourceRecord>> fetchPage() {
		return Mono.from( sourceRecordService.getUnprocessedRecords( Pageable.from(0, PAGE_SIZE) ));
	}
	
	protected Mono<JobChunk<IngestOperation>> getChunk() {
		return fetchPage()
			.flatMap( this::buildChunkFromPage );
	}
	
	@Transactional(readOnly = true)
	protected Mono<SourceToIngestRecordConverter> getConverterForHostLms( @Nullable UUID hostLmsId ) {
		
		final String hostLmsIdStr = hostLmsId.toString();
		var conv = sourceRecConverterCache.get( hostLmsIdStr );
		
		if (conv == null) {
			synchronized( sourceRecConverterCache ) {
				conv = sourceRecConverterCache.get( hostLmsIdStr );
				if (conv == null) {					
					conv = hostLmsService
						.findById( hostLmsId )
						.flatMap( hostLmsService::getIngestSourceFor )
						.filter( SourceToIngestRecordConverter.class::isInstance )
						.cast( SourceToIngestRecordConverter.class )
						.doOnNext( val -> log.info("Cacheing SourceToIngestRecordConverter for [{}]", hostLmsIdStr))
						.cache();
					
					sourceRecConverterCache.put(hostLmsIdStr, conv);
				}
			}
		} else {
			log.debug("Returning SourceToIngestRecordConverter for [{}] from cache", hostLmsIdStr);
		}
		// Returning from cache.
		return conv;
	}
	
	@Override
	public @NonNull String getName() {
		return JOB_NAME;
	}
	
	@Override
	@SingleResult
	public Publisher<JobChunk<IngestOperation>> resume( JsonNode lastCheckpoint ) {
		
		if (log.isDebugEnabled()) {
			IngestJobParams jobParams = conversionService.convertRequired(lastCheckpoint, IngestJobParams.class);
			log.debug("Resume with [{}]", jobParams);
		}
		
		return getChunk();
	}

	@Override
	@SingleResult
	public Publisher<JobChunk<IngestOperation>> start() {

		log.info("Initializing ingest job");
		return getChunk();
	}
	
	@NonNull
	private Mono<IngestRecord> tryConvertToIngestRecord( @NonNull SourceRecord sourceRecord ) {
		try {
			return getConverterForHostLms(sourceRecord.getHostLmsId())
				.publishOn( Schedulers.boundedElastic() )
				.subscribeOn( Schedulers.boundedElastic() )
				.mapNotNull( converter -> converter.convertSourceToIngestRecord(sourceRecord));
		} catch (Exception e) {
			log.error("Exception converting source record with ID "+sourceRecord.getId(), e);
			return Mono.error(e);
		}
	}
	
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Publisher<JobChunk<T>> processChunk(JobChunk<T> chunk) {
		Instant processedTime = Instant.now();
		IngestJobChunk ijc = (IngestJobChunk)chunk;
		
		return Flux.fromIterable( ijc.getData() )
			.flatMap(op -> processSingleOperation(op, processedTime)
					
				// Do this error handling here as the mono is set to retry on exception.
				.onErrorResume(err -> opFail(op, processedTime, "Failed to process bib: {}", err)))
			
			.then( Mono.just(chunk) );
			
		
//		throw new IllegalStateException("NOPE!");
//		return Mono.just( chunk );
	}

	@Retryable
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<BibRecord> processSingleOperation( IngestOperation op, Instant processedTime ) {
		
		IngestRecord ir = op.getIngest();
		if (ir == null) {
			// Couldn't create an ingest record.
      log.error("Failed to create Ingest Record from source record "+op.getSourceId());
			return opFail(op, processedTime, "Failed to create IngestRecord from source ");
		}
		
		return bibRecordService.process( ir )

			.switchIfEmpty( opSuccess(op, processedTime, "No returned Bib. Assumed redacted or without sufficient title info") )
			
			// Returned bib (Not delete operation), try cluster.
			.flatMap( bib -> processNoneDelete( op, bib, processedTime ));
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<BibRecord> processNoneDelete (IngestOperation op, BibRecord theBib, Instant processedTime) {
		// Returned bib, try cluster.
		return recordClusteringService.clusterBib(theBib)
				
			// Successfully clustered the bib.
			.flatMap( bib ->
					opSuccess(op, processedTime, "Bib [%s] created/updated".formatted(bib.getId()) )
						.thenReturn(bib))
			
			// Empty means there was an error.
			// Log it, but assume that upstream has completed without error for a reason.
			.switchIfEmpty( opFail(op, processedTime, "Failed to process bib, processing returned empty result.") );
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Mono<T> opSuccess (IngestOperation op, Instant time, String info) {
		// Update the details of the SourceRecord
		if (log.isDebugEnabled()) {
			log.debug("Success: {}", info);
		}
		return sourceRecordService.updateProcessingInformation(op.getSourceId(), time, ProcessingStatus.SUCCESS, info).then(Mono.empty());
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Mono<T> opFail (IngestOperation op, Instant time, String info) {
		// Update the details of the SourceRecord
		log.warn("opFail: {}", info);
		return sourceRecordService.updateProcessingInformation(op.getSourceId(), time, ProcessingStatus.FAILURE, info).then(Mono.empty());
	}
	

	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Mono<T> opFail (IngestOperation op, Instant time, String infoTemplate, Throwable error) {
		return opFail(op, time, infoTemplate.formatted(error.getMessage()))
			.thenReturn( error )
			.flatMap( Mono::error );
	}

	private void jobSubscriber( long time, long count ) {
		
		if (count < 1) {
			log.info("No records to process");
			return;
		}
		
		float rate = (float)count / ((float)time / 1000f);
		
		Duration elapsed = Duration.ofMillis(time);
		
		log.info("Processed {} source records. Total time {} hours, {} minute and {} seconds (rate of {} per second)", count,
				elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart(), "%,.2f".formatted(rate));
	}
	
	private void errorSubscriber ( Throwable t ) {
		log.error("Error during import job", t);
	}
	
	
	
	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "40s", fixedDelay = "2m")
	public void scheduleJob() {
		
		Mono.just( this )
			.publishOn( Schedulers.boundedElastic() )
			.subscribeOn( Schedulers.boundedElastic() )
			.flatMapMany( jobRunnerService::processJobInstance )
			.map(chunk -> Optional.ofNullable(chunk.getData()) // Extract resource count.
					.map( Collection::size )
					.map( Integer::longValue )
					.orElse(0L))
			.reduce( Long::sum )
			.elapsed()
			.transformDeferred(lockService.withLockOrEmpty("ingest-job"))
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(JOB_NAME + "allready running (NOOP)");
				} 
			})
			.subscribe(
					TupleUtils.consumer(this::jobSubscriber),
					this::errorSubscriber);
	}
}
