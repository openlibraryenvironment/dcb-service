package org.olf.dcb.indexing.job;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.indexing.SharedIndexService;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.retry.RetrySpec;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.jobs.Job;
import services.k_int.jobs.JobChunk;
import services.k_int.jobs.JobChunkProcessor;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;
import services.k_int.jobs.ReactiveJobRunnerService;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Slf4j
@Singleton
@RequiredArgsConstructor
@Requires(bean = SharedIndexService.class)
@ApplicableChunkTypes( IndexJobChunk.class )
public class IndexSynch implements Job<UUID>, JobChunkProcessor {

	private final static String JOB_ID = "indexing-job";
	private final static String JOB_NAME = "Indexing Job";
	private final ReactiveJobRunnerService jobRunnerService;
  private final ReactorFederatedLockService lockService;
	private final RecordClusteringService clusters;
	private final SharedIndexService indexer;
	
  private final ObjectMapper mapper;
  
  @Data
  @Builder
  @Serdeable
  protected static class JobParameters {
  	
  	@NotNull
  	@NonNull
  	private Instant cutoff;
  	
  	@Builder.Default
  	private int maxPageSize = 1000;
  }

	@NonNull
	@Override
	public String getName() {
		return JOB_NAME;
	}
	
	@NonNull
	private JobParameters getInitialParameters() {
		return JobParameters.builder()
			.cutoff(Instant.now())
			.build();
	}
	
	@NonNull
	private JobParameters parseParams( JsonNode json ) {
		
		if (json == null) return getInitialParameters();
		
		try {
			return mapper.readValueFromTree(json, JobParameters.class);
		} catch (IOException e) {
			log.warn("Error parsing parameters from checkopoint. Returning initial values.");
		}
		
		return getInitialParameters();
	}
	
	@Nullable
	private JsonNode paramsToJson( JobParameters params ) {
		try {
			return mapper.writeValueToTree(params);
		} catch (IOException e) {
			log.warn("Error writing parameters to json, returning null.");
		}
		
		return null;
	}

	@NonNull
	@Override
	public Publisher<JobChunk<UUID>> resume(JsonNode lastCheckpoint) {
		
		return getChunkFromParameters( parseParams(lastCheckpoint) );
	}

	@NonNull
	@Override
	public Publisher<JobChunk<UUID>> start() {
		return getChunkFromParameters( getInitialParameters() );
	}

	@NonNull
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Publisher<JobChunk<T>> processChunk(JobChunk<T> chunk) {
		IndexJobChunk indexChunk = (IndexJobChunk) chunk;
		
		return Flux.just( (List<UUID>) new ArrayList<>( indexChunk.getData() ))
			.transform( indexer::expandAndProcess )
			
			// Empty suggests the indexer suppressed the operation (probably because the search service is
			// down. Lets transalte that into an error here so that the retry block will capture it, and then retry
			// with backoff.
			.switchIfEmpty(Mono.defer(() -> chunk.getData().isEmpty() ? Mono.empty() : Mono.error(new DcbError("Temporary error while indexing. Retry"))))
			
			// Retry this every minute for a max of 1 hour (60 times)...
			.retryWhen(
				RetrySpec.fixedDelay(60, Duration.ofMinutes(1))
					.transientErrors(true))
			.then(Mono.just( chunk ))
			.doOnNext( i -> log.info("Seeing onNext") );
	}
	
	private Publisher<JobChunk<UUID>> getChunkFromParameters(JobParameters params) {
		return getNextPage(params.getCutoff(), Pageable.from(0, params.getMaxPageSize()))
			.map( idList -> IndexJobChunk.builder()
				.jobId( getId() )
				.lastChunk( idList.getTotalSize() < params.getMaxPageSize())
				.checkpoint( paramsToJson(params) )
				.data( idList.getContent() )
				.build());
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	protected Mono<Page<UUID>> getNextPage(@NonNull Instant before, @NonNull Pageable page) {
		return clusters.findNextPageIndexedBefore(before, page);
	}
	
	@AppTask
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Scheduled(initialDelay = "20s")
	public void scheduleJob() {
		buildIndexingStream()
		
    	// Lock operator returns empty if not acquired
		  .transformDeferred(lockService.withLockOrEmpty(JOB_ID))
			.subscribeOn(Schedulers.boundedElastic())
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(getName() + "allready running (NOOP)");
				}
			})
			.subscribe(
				TupleUtils.consumer(this::jobSubscriber), this::errorSubscriber);
	}
	
	private Mono<JobChunk<UUID>> deleteOnLastChunk(JobChunk<UUID> chunk) {
		return Mono.justOrEmpty(chunk)
			.filter( JobChunk::isLastChunk )
			.mapNotNull( JobChunk::getCheckpoint )
			.mapNotNull( this::parseParams )
			.mapNotNull( JobParameters::getCutoff )
			.map( cutoff -> {
				// Create a side-effect task that will fire in 30 seconds, and keep retrying (up to 10 times)
				Mono.just(cutoff)
					.delayElement(Duration.ofSeconds(30))
					.doOnNext( c -> log.info("Attempting to delete unseen documents from index") )
					.flatMap( indexer::deleteDocsIndexedBefore )
					.doOnError( t -> log.warn("Error attempting to delete documents from Index.", t) )
					.retry(10)
					.subscribe();
				
				return cutoff;
			})
			.thenReturn(chunk);
	}
	
	private Mono<Tuple2<Long, Long>> buildIndexingStream() {
    return Mono.just(this)
      .flatMapMany(jobRunnerService::processJobInstance)
      .flatMap( chunk -> deleteOnLastChunk(chunk))
      .map( chunk -> Optional.of( chunk.getData() )
	      .map( Collection::size )
	      .map( Integer::longValue )
	      .orElse(0L))
      .reduce(Long::sum)
      .elapsed();
  }

	@ExecuteOn(TaskExecutors.BLOCKING)
	public void tryStartJob() {
		jobRunnerService.resetJob(this)
			.then(buildIndexingStream())
			.transformDeferred(lockService.withLockOrEmpty(JOB_ID))
			.subscribeOn(Schedulers.boundedElastic())
			.doOnSuccess( res -> {
				if (res == null) {
					log.info(getName() + "Job cannot be maually started as it's allready running (NOOP)");
				}
			})
			.subscribe(
				TupleUtils.consumer(this::jobSubscriber), this::errorSubscriber);
	}
	

	private void jobSubscriber(long time, long count) {

		if (count < 1) {
			log.info("Nothing to index");
			return;
		}

		float rate = (float) count / ((float) time / 1000f);

		Duration elapsed = Duration.ofMillis(time);

		log.info("(Re)Indexed {} cluster records. Total time {} hours, {} minute and {} seconds (rate of {} per second)", count,
				elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart(), "%,.2f".formatted(rate));
	}

	private void errorSubscriber(Throwable t) {
		log.error("*** Error during import job {} ***", t.getMessage(), t);
	}
}
