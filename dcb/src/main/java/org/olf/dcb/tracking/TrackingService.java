package org.olf.dcb.tracking;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.LenderTrackingEvent;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.tracking.model.TrackingRecord;
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
import io.micronaut.context.event.ApplicationEventPublisher;

@Refreshable
@Singleton
public class TrackingService implements Runnable {

  private Disposable mutex = null;
  private Instant lastRun = null;

  private static Logger log = LoggerFactory.getLogger(TrackingService.class);

	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;
	private SupplyingAgencyService supplyingAgencyService;
        private final ApplicationEventPublisher<TrackingRecord> eventPublisher;
        // private final ApplicationEventPublisher<StateChange> stateChangeEventPublisher;

	TrackingService( PatronRequestRepository patronRequestRepository,
			 SupplierRequestRepository supplierRequestRepository,
                         SupplyingAgencyService supplyingAgencyService,
                         ApplicationEventPublisher<TrackingRecord> eventPublisher
                        ) {
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.eventPublisher = eventPublisher;
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

                // We fetch the state of the hold at the supplying library. If it is different to the last state
                // we stashed in SupplierRequest.localStatus then we have detected a change. We emit an event to let
                // observers know that the state has changed but we DO NOT directly update localStatus. the handler
                // will arrange for localStatus to be updated once it's action has completed successfully. This menans
                // that if a handler fails to complete, on the next iteration this event will fire again, giving us a
                // means to try and recover from failure scenarios. For example, when we detect that a supplying system
                // has changed to "InTransit" the handler needs to update the pickup site and the patron site, if either of
                // these operations fail, we don't update the state - which will cause the handler to re-fire until
                // successful completion.
		return supplyingAgencyService.getHold(sr.getHostLmsCode(), sr.getLocalId())
			.onErrorContinue((e, o) -> {
				log.error("Error occurred: " + e.getMessage(),e);
			})
                        .filter ( hold -> !hold.getStatus().equals(sr.getLocalStatus()) )
			.map( hold -> { 
				log.debug("current request status: {}",hold);
                                StateChange sc = StateChange.builder()
                                                            .resourceType("SupplierRequest")
                                                            .resourceId(sr.getLocalId())
                                                            .fromState(sr.getLocalStatus())
                                                            .toState(hold.getStatus())
                                                            .build();
                                // SupplierRequestHold.StatusChange id fromstate tostate
                                log.debug("Publishing state change event {}",sc);
                                // stateChangeEventPublisher.publishEvent(sc);

                                // We use a synchronous event here because we don't want to launch a load of
                                // parallel work (At least not initially)
                                eventPublisher.publishEvent(sc);

                                // sr.setLocalStatus(hold.getStatus());
				return sr;
			});
                        // .flatMap( usr -> Mono.fromDirect(supplierRequestRepository.saveOrUpdate(usr) ));
	}
}

