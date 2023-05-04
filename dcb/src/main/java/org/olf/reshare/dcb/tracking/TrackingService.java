package org.olf.reshare.dcb.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.core.BibRecordService;
import org.olf.reshare.dcb.core.RecordClusteringService;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.micronaut.scheduling.processor.AppTask;
import reactor.core.scheduler.Schedulers;
import org.olf.reshare.dcb.tracking.model.TrackingRecord;

@Refreshable
@Singleton
public class TrackingService implements Runnable {

        private Disposable mutex = null;
        private Instant lastRun = null;

        private static Logger log = LoggerFactory.getLogger(TrackingService.class);

        private final List<TrackingSourcesProvider> sourceProviders;

        TrackingService(List<TrackingSourcesProvider> sourceProviders) {
                this.sourceProviders = sourceProviders;
        }

        @javax.annotation.PostConstruct
        private void init() {
                log.info("TrackingService::init - providers:{}",sourceProviders.toString());
        }

	private void process(TrackingRecord tr) {
		log.debug("process {}",tr);
	}

        public Flux<TrackingRecord> getTrackingRecordStream() {
            	// ./dcb/src/main/java/org/olf/reshare/dcb/core/HostLmsService.java - sourceProviders ends up here
		log.debug("getTrackingRecordStream()");
                return Flux.fromIterable(sourceProviders)
                        .concatMap(provider -> provider.getTrackingSources())
			.flatMap(source -> source.getTrackingData())
			.log();
        }

        private Runnable cleanUp() {

                final var me = this;

                return () -> {
                        log.info("Removing mutex");
                        me.lastRun = Instant.now();
                        me.mutex = null;
                        log.info("Mutex now set to {}", me.mutex);
                };
        }


        // @AppTask
        @Override
        @Scheduled(initialDelay = "1m", fixedDelay = "${dcb.tracking.interval:1h}")
        public void run() {
                log.debug("DCB Tracking Service run");

                if (this.mutex != null && !this.mutex.isDisposed()) {
                        log.info("Ingest already running skipping. Mutex: {}", this.mutex);
                        return;
                }

                log.info("Scheduled Ingest");

                final long start = System.currentTimeMillis();

                // Interleave sources to form 1 flux of ingest records.
                this.mutex = getTrackingRecordStream()

                        // General handlers.
                        .doOnCancel(cleanUp()) // Don't change the last run
                        .onErrorResume(t -> {
                                log.error("Error ingesting tracking records {}", t.getMessage());
                                t.printStackTrace();
                                cleanUp().run();
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
                        .subscribe();
        }
}

