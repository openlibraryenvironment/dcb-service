package org.olf.reshare.dcb.tracking;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.olf.reshare.dcb.tracking.model.LenderTrackingEvent;
import org.olf.reshare.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Refreshable
@Singleton
public class TrackingService implements Runnable {

  private Disposable mutex = null;
  private Instant lastRun = null;

  private static Logger log = LoggerFactory.getLogger(TrackingService.class);

	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;

	TrackingService( PatronRequestRepository patronRequestRepository,
			 SupplierRequestRepository supplierRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@javax.annotation.PostConstruct
	private void init() {
	}

	@AppTask
	@Override
	@Scheduled(initialDelay = "2m", fixedDelay = "${dcb.tracking.interval:5m}")
	public void run() {
		log.debug("DCB Tracking Service run");

		if ( ( this.mutex != null && !this.mutex.isDisposed() ) ) {
			log.info("Tracking already running skipping. Mutex: {}", this.mutex);
			return;
		}

		log.info("Scheduled Tracking Ingest");
		final long start = System.currentTimeMillis();
		trackActivePatronRequestHolds()
			.flatMap( this::checkPatronRequest)
			.subscribe();
		trackActiveSupplierHolds()
			.flatMap( this::checkSupplierRequest)
			.subscribe();
	}

	public Flux<PatronRequest> trackActivePatronRequestHolds() {
		log.debug("trackActivePatronRequestHolds()");
		return Flux.from(patronRequestRepository.findTrackedPatronHolds());
	}

	public Flux<SupplierRequest> trackActiveSupplierHolds() {
		log.debug("trackActiveSupplierHolds()");
		return Flux.from(supplierRequestRepository.findTrackedSupplierHolds());
	}

	private Mono<PatronRequest> checkPatronRequest(PatronRequest pr) {
		log.debug("Check patron request {}",pr);
		return Mono.just(pr);
	}

	private Mono<SupplierRequest> checkSupplierRequest(SupplierRequest sr) {
		log.debug("Check supplier request {}",sr);
		return Mono.just(sr);
	}
}

