package org.olf.reshare.dcb.ingest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.olf.reshare.dcb.bib.BibRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parallel;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
@Parallel
public class IngestService implements Runnable {

	private Disposable mutex = null;
	private Instant lastRun = null;

	private static Logger log = LoggerFactory.getLogger(IngestService.class);
	private final BibRecordService bibRecordService;

	private final List<IngestSource> ingestSources;

	IngestService(BibRecordService bibRecordService, List<IngestSource> ingestSources) {
		this.bibRecordService = bibRecordService;
		this.ingestSources = ingestSources;
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
					Flux.fromIterable(ingestSources)
						.map(source -> source.apply(lastRun)))
				
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
					
				}).count().subscribe(count -> {
					bibRecordService.cleanup();
					cleanUp(Instant.ofEpochMilli(start)).run();

					if (count < 1) {
						log.info("No records to import");
						return;
					}

					final Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - start);
					log.info("Finsihed adding {} records. Total time {} hours, {} minute and {} seconds", count,
							elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
				});
	}
}
