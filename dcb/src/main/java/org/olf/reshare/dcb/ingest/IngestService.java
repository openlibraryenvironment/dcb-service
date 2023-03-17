package org.olf.reshare.dcb.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Refreshable
@Singleton
//@Parallel
public class IngestService implements Runnable {

	public static final String TRANSFORMATIONS_RECORDS = "ingest-records";
	public static final String TRANSFORMATIONS_BIBS = "ingest-bibs";

	private Disposable mutex = null;
	private Instant lastRun = null;

	private static Logger log = LoggerFactory.getLogger(IngestService.class);

	private final BibRecordService bibRecordService;

	private final List<IngestSourcesProvider> sourceProviders;
	private final PublisherTransformationService publisherTransformationService;

	IngestService(BibRecordService bibRecordService, List<IngestSourcesProvider> sourceProviders, PublisherTransformationService publisherHooksService) {
		this.bibRecordService = bibRecordService;
		this.sourceProviders = sourceProviders;
		this.publisherTransformationService = publisherHooksService;
	}


	@javax.annotation.PostConstruct
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

	public Flux<BibRecord> getBibRecordStream() {		
		
		return Flux.merge(
				Flux.fromIterable(sourceProviders)
				.concatMap(provider -> provider.getIngestSources())
				.filter(source -> {
					if ( source.isEnabled() ) return true;
					log.info ("Ingest from source: {} has been disabled in config", source.getName());

					return false;
				})
				.map(source -> source.apply(lastRun))
				.onErrorResume(t -> {
					log.error("Error ingesting data {}", t.getMessage());
					t.printStackTrace();
					return Mono.empty();
				}))
				.transform(publisherTransformationService.getTransformationChain(TRANSFORMATIONS_RECORDS)) // Apply any hooks for "ingest-records"

				// Interleaved source stream from all source results.
				// Process them using the pipeline steps...
				.flatMap(bibRecordService::process)
				.transform(publisherTransformationService.getTransformationChain(TRANSFORMATIONS_BIBS)); // Apply any hooks for "ingest-bibs";
	}

	@Override
	@Scheduled(initialDelay = "2s", fixedDelay = "${dcb.ingest.interval:1h}")
	@AppTask
	public void run() {

		if (this.mutex != null && !this.mutex.isDisposed()) {
			log.info("Ingest already running skipping. Mutex: {}", this.mutex);
			return;
		}

		log.info("Scheduled Ingest");

		final long start = System.currentTimeMillis();

		// Interleave sources to form 1 flux of ingest records.
		this.mutex = getBibRecordStream()

			// General handlers.
			.doOnCancel(cleanUp(lastRun)) // Don't change the last run
			.onErrorResume(t -> {
				log.error("Error ingesting records {}", t.getMessage());
				t.printStackTrace();
				cleanUp(lastRun).run();

				return Mono.empty();

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
