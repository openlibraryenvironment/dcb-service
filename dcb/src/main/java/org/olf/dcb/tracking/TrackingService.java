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
                // Check supplier request SupplierRequest(id=3b5db82c-8568-4b1b-8d86-5369e2855f2c, 
                // patronRequest=PatronRequest(id=7a7519a4-3702-478d-ae4c-65436015fa7b, dateCreated=null, 
                // dateUpdated=null, patron=null, bibClusterId=null, pickupLocationCode=null, 
                // statusCode=null, localRequestId=null, localRequestStatus=null), 
                // localItemId=1017281, localItemBarcode=30800004002116, 
                // localItemLocationCode=ab8, hostLmsCode=SANDBOX, statusCode=PLACED, localId=407607, localStatus=0)
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
                                eventPublisher.publishEvent(sc);
                                sr.setLocalStatus(hold.getStatus());
				return sr;
			})
                        .flatMap( usr -> Mono.fromDirect(supplierRequestRepository.saveOrUpdate(usr) ));
	}
}

