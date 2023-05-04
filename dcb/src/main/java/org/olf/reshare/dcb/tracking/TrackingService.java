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

        @Override
        @Scheduled(initialDelay = "1m", fixedDelay = "${dcb.ingest.interval:1h}")
        @AppTask
        public void run() {
                log.debug("DCB Tracking Service run");
        }
}

