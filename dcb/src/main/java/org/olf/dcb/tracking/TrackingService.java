package org.olf.dcb.tracking;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;
import java.time.Instant;

import javax.transaction.Transactional;
import org.olf.dcb.request.resolution.HostLmsReactions;

@Refreshable
@Singleton
public class TrackingService implements Runnable {

  private Disposable mutex = null;
  private Instant lastRun = null;

  private static Logger log = LoggerFactory.getLogger(TrackingService.class);

	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;
	private SupplyingAgencyService supplyingAgencyService;
        private final HostLmsService hostLmsService;
	private HostLmsReactions hostLmsReactions;

	TrackingService( PatronRequestRepository patronRequestRepository,
			 SupplierRequestRepository supplierRequestRepository,
                         SupplyingAgencyService supplyingAgencyService,
                         HostLmsService hostLmsService,
                         HostLmsReactions hostLmsReactions
                        ) {
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.hostLmsReactions = hostLmsReactions;
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
		trackActivePatronRequestHolds().flatMap( this::checkPatronRequest).subscribe();
		trackSupplierItems().flatMap( this::checkSupplierItem).subscribe();
		trackActiveSupplierHolds().flatMap( this::checkSupplierRequest).subscribe();
		trackVirtualItems().flatMap( this::checkVirtualItem).subscribe();
	}

	public Flux<PatronRequest> trackActivePatronRequestHolds() {
		log.debug("trackActivePatronRequestHolds()");
		return Flux.from(patronRequestRepository.findTrackedPatronHolds());
	}

	// Track the state of items created in borrowing and pickup locations to track loaned (Or in transit) items
	public Flux<PatronRequest> trackVirtualItems() {
					log.debug("trackVirtualItems()");
					return Flux.from(patronRequestRepository.findTrackedVirtualItems());
	}

	public Flux<SupplierRequest> trackSupplierItems() {
		log.debug("trackSupplierItems()");
		return Flux.from(supplierRequestRepository.findTrackedSupplierItems());
	}

	public Flux<SupplierRequest> trackActiveSupplierHolds() {
		log.debug("trackActiveSupplierHolds()");
		return Flux.from(supplierRequestRepository.findTrackedSupplierHolds());
	}

private Mono<PatronRequest> checkPatronRequest(PatronRequest pr) {
		log.debug("Check patron request {}", pr);
		return hostLmsService.getClientFor( pr.getPatronHostlmsCode() )
			.flatMap(client -> client.getHold( pr.getLocalRequestId() ))
			.onErrorContinue((e, o) -> log.error("Error occurred: " + e.getMessage(),e))
			.filter ( hold -> !hold.getStatus().equals(pr.getLocalRequestStatus()) )
			.flatMap( hold -> {
				log.debug("current request status: {}",hold);
				StateChange sc = StateChange.builder()
					.resourceType("PatronRequest")
					.resourceId(pr.getId().toString())
					.fromState(pr.getLocalRequestStatus())
					.toState(hold.getStatus())
					.resource(pr)
					.build();
				log.debug("Publishing state change event {}",sc);
				return hostLmsReactions.onTrackingEvent(sc)
					.thenReturn(pr);
			});
	}

	public Mono<PatronRequest> checkVirtualItem(PatronRequest pr) {
		log.debug("Check (local) virtualItem from patron request {} {} {}",pr.getLocalItemId(),pr.getLocalItemStatus(),pr.getPatronHostlmsCode());
                if ( ( pr.getPatronHostlmsCode() != null ) && ( pr.getLocalItemId() != null ) ) {
                        return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
                                .flatMap( client -> Mono.from(client.getItem(pr.getLocalItemId())) )
                                .filter ( item -> !item.getStatus().equals(pr.getLocalItemStatus()) )
                                .flatMap ( item -> {
                                        log.debug("Detected borrowing system - virtual item status change {} to {}",pr.getLocalItemStatus(),item.getStatus());
                                        StateChange sc = StateChange.builder()
                                                            .resourceType("BorrowerVirtualItem")
                                                            .resourceId(pr.getId().toString())
                                                            .fromState(pr.getLocalItemStatus())
                                                            .toState(item.getStatus())
                                                            .resource(pr)
                                                            .build();


                                        log.debug("Publishing state change event {}",sc);
				        return hostLmsReactions.onTrackingEvent(sc)
                                                .thenReturn(pr);
                                });
                }
                else {
                        log.warn("Trackable local item - NULL");
                        return Mono.just(pr);
                }
        }
	public Mono<SupplierRequest> checkSupplierItem(SupplierRequest sr) {
		log.debug("Check (hostlms) supplierItem from supplier request {} {} {}",sr.getLocalItemId(),sr.getLocalItemStatus(),sr.getHostLmsCode());
		if ( ( sr.getHostLmsCode() != null ) && ( sr.getLocalItemId() != null ) ) {
			return hostLmsService.getClientFor(sr.getHostLmsCode())
				.flatMap( client -> Mono.from(client.getItem(sr.getLocalItemId())) )
				.filter ( item -> !item.getStatus().equals(sr.getLocalItemStatus()) )
				.flatMap ( item -> {
					log.debug("Detected supplying system - supplier item status change {} to {}",sr.getLocalItemStatus(),item.getStatus());
					StateChange sc = StateChange.builder()
						.resourceType("SupplierItem")
						.resourceId(sr.getId().toString())
						.fromState(sr.getLocalItemStatus())
						.toState(item.getStatus())
						.resource(sr)
						.build();

					log.debug("Publishing state change event {}",sc);
					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(sr);
				});
		}
		else {
			log.warn("Trackable local item - NULL");
			return Mono.just(sr);
		}
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public Mono<SupplierRequest> checkSupplierRequest(SupplierRequest sr) {
		log.debug("Check supplier request {}",sr.getId(),sr.getLocalStatus());

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
			.flatMap( hold -> {
				log.debug("current request status: {}",hold);
                                StateChange sc = StateChange.builder()
                                                            .resourceType("SupplierRequest")
                                                            .resourceId(sr.getId().toString())
                                                            .fromState(sr.getLocalStatus())
                                                            .toState(hold.getStatus())
                                                            .resource(sr)
                                                            .build();
                                log.debug("Publishing state change event {}",sc);
				return hostLmsReactions.onTrackingEvent(sc)
                                        .thenReturn(sr);
			});
	}
}

