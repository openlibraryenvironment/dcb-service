package org.olf.dcb.ingest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.retry.annotation.Retryable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.micronaut.concurrency.ConcurrencyGroupService;

@Singleton
public class IngestService {

	public static final String TRANSFORMATIONS_RECORDS = "ingest-records";
	public static final String TRANSFORMATIONS_BIBS = "ingest-bibs";
		
	@Value("${dcb.shutdown.maxwait:0}")
	protected long maxWaitTime;
	
	private static final int INGEST_PASS_RECORDS_THRESHOLD = 100_000;

	private MonoSink<String> terminationReason;
	
	private static Logger log = LoggerFactory.getLogger(IngestService.class);

	private final BibRecordService bibRecordService;

	private final List<IngestSourcesProvider> sourceProviders;
	private final PublisherTransformationService publisherTransformationService;
	private final RecordClusteringService recordClusteringService;
  private final ConcurrencyGroupService concurrency;
  

	protected IngestService(BibRecordService bibRecordService, 
		List<IngestSourcesProvider> sourceProviders, 
		PublisherTransformationService publisherHooksService, 
		RecordClusteringService recordClusteringService,
		ConversionService conversionService,
		ConcurrencyGroupService concurrencyGroupService) {

		this.bibRecordService = bibRecordService;
		this.sourceProviders = sourceProviders;
		this.publisherTransformationService = publisherHooksService;
		this.recordClusteringService = recordClusteringService;
		this.concurrency = concurrencyGroupService;
	}


	@jakarta.annotation.PostConstruct
	private void init() {
		log.info("IngestService::init - providers:{}",sourceProviders.toString());
	}
	
	private Flux<IngestRecord> getRecordsFromSource( IngestSource ingestSource, Mono<String> terminator ) {
		return Flux.just( ingestSource )
			.doOnNext(ingestRecordPublisher -> log.debug("returning record publisher: {}", ingestRecordPublisher.toString()))
			
			.flatMap( source -> {
				return source.apply(null, terminator);
			}) // Always pass (instead of lastRun) in null now as the last run is no longer a full run.
			
			.doOnNext(ir -> log.debug("Ingest source {}", ir))
			.limitRate( INGEST_PASS_RECORDS_THRESHOLD / 10, 0 )
			.onErrorResume( t -> {
				log.error( "Error ingesting data {}", t.getMessage() );
				t.printStackTrace();
				return Mono.empty();
			});
	}

	// @Transactional
	@ExecuteOn(TaskExecutors.BLOCKING)
	public Flux<BibRecord> getBibRecordStream() {
		
		// Create the terminator...
		final Mono<String> terminator = Mono.<String>create( sink -> {
			
			terminationReason = sink;
			
		}).cache(); // Hot subscription.
		
		// Subscribe with listener...
		terminator
			.subscribe( _reason -> log.info("Ingest passed threshold of [{}]. Signal graceful stop.", INGEST_PASS_RECORDS_THRESHOLD));
		
		// Counter
		final AtomicInteger counter = new AtomicInteger(0);
		
		return Flux.fromIterable(sourceProviders)
			.flatMap(provider -> provider.getIngestSources())
			.filter(source -> {
				if ( source.isEnabled() ) {
					log.trace ("Ingest from source: {} is enabled", source.getName());
					return true;
				}
				log.info ("Ingest from source: {} has been disabled in config", source.getName());
	
				return false;
			})
			
			.transform( concurrency.toGroupedSubscription( subscribeToSource(terminator) ) )

			.doOnNext( _ir -> {
				int count = counter.addAndGet(1);
				
				if (count < INGEST_PASS_RECORDS_THRESHOLD) return;
				terminationReason.success("Ingest passed threshold of [" + INGEST_PASS_RECORDS_THRESHOLD + "]");
			})
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


	protected Function<IngestSource, Publisher<IngestRecord>> subscribeToSource(final Mono<String> terminator) {
		return source -> {
			return getRecordsFromSource(source, terminator);
		};
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
			.doOnError ( throwable -> log.warn("ONERROR Error after clustering step", throwable) );
	}	
}
