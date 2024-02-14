package org.olf.dcb.ingest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.olf.dcb.core.AppState;
import org.olf.dcb.core.AppState.AppStatus;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.ingest.model.IngestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.micronaut.concurrency.ConcurrencyGroupService;
import services.k_int.micronaut.scheduling.processor.AppTask;

//@Refreshable
@Singleton
//@Parallel
public class IngestService implements Runnable {

	public static final String TRANSFORMATIONS_RECORDS = "ingest-records";
	public static final String TRANSFORMATIONS_BIBS = "ingest-bibs";
	
	private static final int INGEST_PASS_RECORDS_MAX = 100000;

	private Disposable mutex = null;
	private Instant lastRun = null;

	private static Logger log = LoggerFactory.getLogger(IngestService.class);

	private final BibRecordService bibRecordService;

	private final List<IngestSourcesProvider> sourceProviders;
	private final PublisherTransformationService publisherTransformationService;
	private final RecordClusteringService recordClusteringService;
	private final HazelcastInstance hazelcastInstance;
	private FencedLock lock;
  private final AppState appState;
  private final ConcurrencyGroupService concurrency;

	IngestService(BibRecordService bibRecordService, 
		List<IngestSourcesProvider> sourceProviders, 
		PublisherTransformationService publisherHooksService, 
		RecordClusteringService recordClusteringService, 
		HazelcastInstance hazelcastInstance,
		ConversionService conversionService,
		AppState appState, ConcurrencyGroupService concurrencyGroupService) {

		this.bibRecordService = bibRecordService;
		this.sourceProviders = sourceProviders;
		this.publisherTransformationService = publisherHooksService;
		this.recordClusteringService = recordClusteringService;
		this.hazelcastInstance = hazelcastInstance;
		this.appState = appState;
		this.concurrency = concurrencyGroupService;
	}


	@jakarta.annotation.PostConstruct
	private void init() {
		log.info("IngestService::init - providers:{}",sourceProviders.toString());
	}


	private Runnable cleanUp(final Instant i) {
		final var me = this;

		return () -> {
			log.info("Removing mutex");
			me.lastRun = i;
			me.mutex = null;

			log.info("Mutex now set to {}", me.mutex);
		};
	}
	
	private Flux<IngestRecord> getRecordsFromSource( IngestSource ingestSource ) {
		return Flux.just( ingestSource )
			.doOnNext(ingestRecordPublisher -> log.debug("returning record publisher: {}", ingestRecordPublisher.toString()))
			
			.flatMap( source -> source.apply(null) ) // Always pass (instead of lastRun) in null now as the last run is no longer a full run.
			.limitRate( INGEST_PASS_RECORDS_MAX / 10, 0 )
			.onErrorResume( t -> {
				log.error( "Error ingesting data {}", t.getMessage() );
				t.printStackTrace();
				return Mono.empty();
			});
	}

	// @Transactional
	@ExecuteOn(TaskExecutors.BLOCKING)
	public Flux<BibRecord> getBibRecordStream() {
		return Flux.fromIterable(sourceProviders)
			.flatMap(provider -> provider.getIngestSources())
			.filter(source -> {
				if ( source.isEnabled() ) return true;
				log.info ("Ingest from source: {} has been disabled in config", source.getName());
	
				return false;
			})
			
			.transform( concurrency.toGroupedSubscription( this::getRecordsFromSource ) )
			.take(INGEST_PASS_RECORDS_MAX, true) // Max number to import.
//				.buffer(2000)
//				.concatMap( items -> {
//					Collections.reverse(items);
//					return Flux.fromIterable(items).distinctUntilChanged( item -> item.getUuid().toString() );
//				})
		
		.transform(publisherTransformationService.getTransformationChain(TRANSFORMATIONS_RECORDS)) // Apply any hooks for "ingest-records"
		
		// Interleaved source stream from all source results.
		// .concatMap(this::processSingleRecord)
		.flatMap(this::processSingleRecord)

		.transform(publisherTransformationService.getTransformationChain(TRANSFORMATIONS_BIBS)) // Apply any hooks for "ingest-bibs";
		.doOnError ( throwable -> log.warn("ONERROR Error after transform step", throwable) );
	}
	

	@Retryable
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Mono<BibRecord> processSingleRecord ( IngestRecord ingestRecord ) {
		log.trace("processSingleRecord {}@{}",ingestRecord.getSourceRecordId(),ingestRecord.getSourceSystem().getCode() );
		return Mono.from(bibRecordService.process( ingestRecord ))
			.doOnNext( br -> log.trace("process ingest record {}@{}", br.getSourceRecordId(), ingestRecord.getSourceSystem().getCode() ) )
			.flatMap(recordClusteringService::clusterBib)
			.map( theBib -> {
				if (theBib.getContributesTo() == null)
					log.warn("Bib {} doesn't have cluster record", theBib);
				
				return theBib;
			})
//			.flatMap(this::reReadBib)
			.doOnError ( throwable -> log.warn("ONERROR Error after clustering step", throwable) );
	}
	
//	@Transactional(propagation = Propagation.MANDATORY)
//	protected Mono<BibRecord> reReadBib ( BibRecord bib ) {
//		return bibRecordService.getById( bib.getId() )
//			.map( theBib -> {
//				if (theBib.getContributesTo() == null)
//					log.warn("Bib {} doesn't have cluster record", theBib);
//				
//				return theBib;
//			});
//	}

	// Extracted from run() method so the shutdown handler can peer inside this instance and determine
	// if an ingest is running.
	public boolean isIngestRunning() {
    if (this.mutex != null && !this.mutex.isDisposed()) {
      log.info("Ingest already running skipping. Mutex: {}", this.mutex);
      return true;
    }
		return false;
	}

	@Override
	@Scheduled(initialDelay = "20s", fixedDelay = "${dcb.ingest.interval:1m}")
	@AppTask
	public void run() {

		// Need to work out how this works with Hazelcast FencedLock
		// lock = hazelcastInstance.getCPSubsystem().getLock("DCBIngestLock");
		if (isIngestRunning()) {
			log.info("Ingest already running skipping. Mutex: {}", this.mutex);
			return;
		}

		if ( appState.getRunStatus() != AppStatus.RUNNING ) {
			log.info("App is shuttuing down - not creating any new tasks");
			return;
		}

		if (lastRun != null && ChronoUnit.MINUTES.between(lastRun, Instant.now()) < 2 ) {
			log.info("At least 2 minutes must elapse between ingest runs. Skipping as last run was {}.", lastRun);
			return;
		}
		
		log.info("Scheduled Ingest");

		final long start = System.currentTimeMillis();

		// Interleave sources to form 1 flux of ingest records.
		this.mutex = getBibRecordStream()

			// General handlers.
			.doOnCancel(cleanUp(Instant.now())) // Don't change the last run
			.onErrorResume(t -> {
				log.error("Error ingesting records {}", t.getMessage());
				t.printStackTrace();
				cleanUp(Instant.now()).run();

				return Mono.empty();

			})
			.doOnComplete(() -> {
				// Ensure we do this lazily to avoid incorrect timestamps.
				cleanUp(Instant.now()).run();
			})

			.count()
			.doOnNext(count -> {
				if (count < 1) {
					log.info("No records to import");
					return;
				}

				final Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - start);
				log.info("Finsihed adding {} records. Total time {} hours, {} minute and {} seconds", count,
						elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
			})

			.then(Mono.from( bibRecordService.cleanup() ))
			.subscribe();
	}
}
