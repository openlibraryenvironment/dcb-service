package org.olf.dcb.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;
import java.time.Instant;

@Slf4j
@Refreshable
@Singleton
public class TrackingService implements Runnable {

	public static final String LOCK_NAME = "tracking-service";
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplyingAgencyService supplyingAgencyService;
	private final HostLmsService hostLmsService;
	private final HostLmsReactions hostLmsReactions;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final ReactorFederatedLockService reactorFederatedLockService;

	TrackingService(PatronRequestRepository patronRequestRepository,
			SupplierRequestRepository supplierRequestRepository,
			SupplyingAgencyService supplyingAgencyService,
			HostLmsService hostLmsService,
			HostLmsReactions hostLmsReactions,
			PatronRequestWorkflowService patronRequestWorkflowService,
			ReactorFederatedLockService reactorFederatedLockService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.hostLmsReactions = hostLmsReactions;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.reactorFederatedLockService = reactorFederatedLockService;
	}

	@AppTask
	@Override
	@Scheduled(initialDelay = "2m", fixedDelay = "${dcb.tracking.interval:5m}")
	public void run() {
		log.debug("DCB Tracking Service run");
		
		
		Flux.concatDelayError(
				Flux.defer(this::trackSupplierItems),
				Flux.defer(this::trackActiveSupplierHolds),
				Flux.defer(this::trackVirtualItems),
				Flux.defer(this::trackActivePatronRequestHolds))
		
			.doOnSubscribe( _s -> log.info("TRACKING Starting Scheduled Tracking Ingest"))
			.onErrorResume( error -> {
				log.error("TRACKING Error enriching collecting request data", error);
				return Mono.empty();
			})
			.thenMany( Flux.defer(this::trackActiveDCBRequests) )
			.transformDeferred(reactorFederatedLockService.withLockOrEmpty(LOCK_NAME))
			.count()
			.subscribe(
					total -> log.info("TRACKING Tracking completed for {} total Requests", total),
					error -> log.error("TRACKING Error when updating tracking information"));

	}

	private Flux<PatronRequest> trackActiveDCBRequests() {
		return Flux.from(patronRequestRepository.findProgressibleDCBRequests())
			.doOnSubscribe(_s -> log.info("TRACKING trackActiveDCBRequests()"))
			.flatMap(this::tryToProgressDCBRequest)
			.transform(enrichWithLogging("TRACKING active DCB request tracking complete", "TrackingError (DCBRequest):"));
	}

	private Flux<PatronRequest> trackActivePatronRequestHolds() {
		return Flux.from(patronRequestRepository.findTrackedPatronHolds())
			.doOnSubscribe(_s -> log.info("TRACKING trackActivePatronRequestHolds()"))
			.flatMap(this::checkPatronRequest)
			.transform(enrichWithLogging("TRACKING active borrower request tracking complete", "TrackingError (PatronHold):"));
	}

	// Track the state of items created in borrowing and pickup locations to track loaned (Or in transit) items
	private Flux<PatronRequest> trackVirtualItems() {
		return Flux.from(patronRequestRepository.findTrackedVirtualItems())
			.doOnSubscribe(_s -> log.info("TRACKING trackVirtualItems()"))
			.flatMap(this::checkVirtualItem)
			.transform(enrichWithLogging("TRACKING active borrower virtual item tracking complete", "TrackingError (VirtualItem):"));
	}

	private <T> Function<Flux<T>, Flux<T>> enrichWithLogging( String successMsg, String errorMsg ) {
		return (source) -> source
			.doOnComplete(() -> log.info(successMsg))
			.doOnError(error -> log.error(errorMsg, error));
	}
	
	private Flux<SupplierRequest> trackSupplierItems() {
		return Flux.from(supplierRequestRepository.findTrackedSupplierItems())
			.doOnSubscribe(_s -> log.info("TRACKING trackSupplierItems()"))
			.flatMap(this::enrichWithPatronRequest)
			.flatMap(this::checkSupplierItem)
			.transform(enrichWithLogging("TRACKING active supplier item tracking complete", "TrackingError (SupplierItem):"));
	}

	private Flux<SupplierRequest> trackActiveSupplierHolds() {
		return Flux.from(supplierRequestRepository.findTrackedSupplierHolds())
				.doOnSubscribe(_s -> log.info("TRACKING trackActiveSupplierHolds()"))
      .flatMap(this::enrichWithPatronRequest)
			.flatMap(this::checkSupplierRequest)
			.transform(enrichWithLogging("TRACKING active supplier hold tracking complete", "TrackingError (SupplierHold):"));
	}



	// The check methods themselves




	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<PatronRequest> checkPatronRequest(PatronRequest pr) {
		log.info("TRACKING Check patron request {}", pr);

		return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
			.flatMap(client -> client.getRequest(pr.getLocalRequestId()))
			.onErrorContinue((e, o) -> log.error("Error occurred: " + e.getMessage(), e))
			.doOnNext(hold -> log.info("TRACKING Compare patron request {} states: {} and {}", pr.getId(), hold.getStatus(), pr.getLocalRequestStatus()))
			.flatMap( hold -> {
				if ( hold.getStatus().equals(pr.getLocalRequestStatus()) ) {
					// The hold status is the same as the last time we checked - update the tracking info and return
					log.debug("TRACKING - update PR repeat counter {} {} {}",pr.getId(), pr.getLocalRequestStatus(), pr.getLocalRequestStatusRepeat());
					return Mono.from(patronRequestRepository.updateLocalRequestTracking(pr.getId(), pr.getLocalRequestStatus(), Instant.now(),
							incrementRepeatCounter(pr.getLocalRequestStatusRepeat())))
						.doOnNext(count -> log.debug("update count {}",count))
						.thenReturn(pr);
				}
				else {
					// The hold status has changed - do something different
					StateChange sc = StateChange.builder()
						.patronRequestId(pr.getId())
						.resourceType("PatronRequest")
						.resourceId(pr.getId().toString())
						.fromState(pr.getLocalRequestStatus())
						.toState(hold.getStatus())
						.resource(pr)
						.build();

					log.info("TRACKING-EVENT PR state change event {}",sc);
					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(pr);
				}
			});
	}

	private Long incrementRepeatCounter(Long current) {
		Long repeat_count = current;

		if ( repeat_count == null ) 
			repeat_count = Long.valueOf(1);
		else
			repeat_count = Long.valueOf(repeat_count.longValue() + 1);

		return repeat_count;
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<PatronRequest> checkVirtualItem(PatronRequest pr) {
		log.info("TRACKING Check (local) virtualItem from patron request {} {} {}",
			pr.getLocalItemId(), pr.getLocalItemStatus(), pr.getPatronHostlmsCode());

		if ((pr.getPatronHostlmsCode() != null) && (pr.getLocalItemId() != null)) {
			return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
				.flatMap(client -> Mono.from(client.getItem(pr.getLocalItemId(), pr.getLocalRequestId())))
				.filter(item -> (
					((item.getStatus() == null) && (pr.getLocalItemStatus() != null)) ||
						((item.getStatus() != null) && (pr.getLocalItemStatus() == null)) ||
						(!item.getStatus().equals(pr.getLocalItemStatus()))
					) )
				.flatMap(item -> {
					log.debug("TRACKING Detected borrowing system - virtual item status change {} to {}",
						pr.getLocalItemStatus(), item.getStatus());
					StateChange sc = StateChange.builder()
						.patronRequestId(pr.getId())
						.resourceType("BorrowerVirtualItem")
						.resourceId(pr.getId().toString())
						.fromState(pr.getLocalItemStatus())
						.toState(item.getStatus())
						.resource(pr)
						.build();

					log.info("TRACKING-EVENT vitem change event {}", sc);
					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(pr);
				});
		}
		else {
			log.warn("Trackable local item - NULL");
			return Mono.just(pr);
		}
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<SupplierRequest> checkSupplierItem(SupplierRequest sr) {
		log.info("TRACKING Check (hostlms) supplierItem from supplier request item={} status={} code={}",
			sr.getLocalItemId(), sr.getLocalItemStatus(), sr.getHostLmsCode());

		if ((sr.getHostLmsCode() != null) && (sr.getLocalItemId() != null)) {
			log.debug("TRACKING hostLms code and itemId present.. continue");

			return hostLmsService.getClientFor(sr.getHostLmsCode())
				.flatMap(client -> Mono.from(client.getItem(sr.getLocalItemId(), sr.getLocalId())))
				.doOnNext(item -> log.debug("Process tracking supplier request item {}", item))
				.filter(item -> !item.getStatus().equals(sr.getLocalItemStatus()) )
				.flatMap(item -> {
					log.debug("Detected supplying system - supplier item status change {} to {}",
						sr.getLocalItemStatus(), item.getStatus());

					StateChange sc = StateChange.builder()
						.patronRequestId(sr.getPatronRequest().getId())
						.resourceType("SupplierItem")
						.resourceId(sr.getId().toString())
						.fromState(sr.getLocalItemStatus())
						.toState(item.getStatus())
						.resource(sr)
						.build();

					log.info("TRACKING-EVENT supplier-item state change {}", sc);
					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(sr);
				});
		}
		else {
			log.warn("TRACKING Trackable local item - NULL");
			return Mono.just(sr);
		}
	}

  // Look up the patron request and add it to the supplier request as a full object instead of a stub
	private Mono<SupplierRequest> enrichWithPatronRequest(SupplierRequest sr) {
		return Mono.from(patronRequestRepository.findById(sr.getPatronRequest().getId()))
			.flatMap(pr -> {
				sr.setPatronRequest(pr);
				return Mono.just(sr);
			});
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<SupplierRequest> checkSupplierRequest(SupplierRequest sr) {
		log.info("TRACKING Check supplier request {} with local status \"{}\"", sr.getId(), sr.getLocalStatus());

		// We fetch the state of the hold at the supplying library. If it is different to the last state
		// we stashed in SupplierRequest.localStatus then we have detected a change. We emit an event to let
		// observers know that the state has changed but we DO NOT directly update localStatus. the handler
		// will arrange for localStatus to be updated once it's action has completed successfully. This means
		// that if a handler fails to complete, on the next iteration this event will fire again, giving us a
		// means to try and recover from failure scenarios. For example, when we detect that a supplying system
		// has changed to "InTransit" the handler needs to update the pickup site and the patron site, if either of
		// these operations fail, we don't update the state - which will cause the handler to re-fire until
		// successful completion.
		return supplyingAgencyService.getRequest(sr.getHostLmsCode(), sr.getLocalId())
			.filter(hold -> !hold.getStatus().equals(sr.getLocalStatus()))
			.flatMap(hold -> {
				log.debug("TRACKING current request status: {}", hold);

				// If the hold has an item and/or a barcode attached, pass it along
				Map<String,Object> additionalProperties = new HashMap<String,Object>();
				if ( hold.getRequestedItemId() != null )
					additionalProperties.put("RequestedItemId", hold.getRequestedItemId());

				StateChange sc = StateChange.builder()
					.patronRequestId(sr.getPatronRequest().getId())
					.resourceType("SupplierRequest")
					.resourceId(sr.getId().toString())
					.fromState(sr.getLocalStatus())
					.toState(hold.getStatus())
					.resource(sr)
					.additionalProperties(additionalProperties)
					.build();

				log.info("TRACKING Publishing state change event for supplier request {}", sc);

				return hostLmsReactions.onTrackingEvent(sc)
					.thenReturn(sr);
			})
			.onErrorResume( error -> Mono.defer(() -> {
				log.error("TRACKING Error occurred: " + error.getMessage(), error);

        StateChange sc = StateChange.builder()
          .patronRequestId(sr.getPatronRequest().getId())
          .resourceType("SupplierRequest")
          .resourceId(sr.getId().toString())
          .fromState(sr.getLocalStatus())
          .toState("ERROR")
          .resource(sr)
          .build();

				return hostLmsReactions.onTrackingEvent(sc)
					.thenReturn(sr);
			}));
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Flux<PatronRequest> tryToProgressDCBRequest(PatronRequest patronRequest) {
		log.debug("TRACKING Attempt to progress {}:{}",patronRequest.getId(),patronRequest.getStatus());
		return patronRequestWorkflowService.progressAll(patronRequest);
	}

}
