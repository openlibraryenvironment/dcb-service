package org.olf.reshare.dcb.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.olf.reshare.dcb.bib.BibRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
//@Parallel
public class IngestService implements Runnable {

	private Disposable mutex = null;
	private Instant lastRun = null;

	private static Logger log = LoggerFactory.getLogger(IngestService.class);
	
	private final BibRecordService bibRecordService;

	private final List<IngestSourcesProvider> sourceProviders;

	IngestService(BibRecordService bibRecordService, List<IngestSourcesProvider> sourceProviders) {
		this.bibRecordService = bibRecordService;
		this.sourceProviders = sourceProviders;
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

	@Override
	@Scheduled(initialDelay = "2s", fixedDelay = "1h")
	@AppTask
	public void run() {

		if (this.mutex != null && !this.mutex.isDisposed()) {
			log.info("Ingest already running skipping. Mutex: {}", this.mutex);
			return;
		}

		log.info("Scheduled Ingest");

		final long start = System.currentTimeMillis();

		// Interleave sources to form 1 flux of ingest records.
		this.mutex = Flux.merge(
			Flux.fromIterable(sourceProviders)
				.flatMap(provider -> provider.getIngestSources())
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
				
				// Interleaved source stream from all source results.
				// Process them using the pipeline steps...
				.flatMap(bibRecordService::process)
				
				// General handlers.
				.doOnCancel(cleanUp(lastRun)) // Don't change the last run
				.onErrorResume(t -> {
					log.error("Error ingesting sources {}", t.getMessage());
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
