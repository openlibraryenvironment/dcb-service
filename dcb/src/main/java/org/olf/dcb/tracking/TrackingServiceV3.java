package org.olf.dcb.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
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
import java.time.Duration;
import io.micrometer.core.annotation.Timed;

@Slf4j
@Refreshable
@Singleton
public class TrackingServiceV3 implements TrackingService {

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplyingAgencyService supplyingAgencyService;
	private final HostLmsService hostLmsService;
	private final HostLmsReactions hostLmsReactions;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final ReactorFederatedLockService reactorFederatedLockService;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	TrackingServiceV3(PatronRequestRepository patronRequestRepository,
                      SupplierRequestRepository supplierRequestRepository,
                      SupplyingAgencyService supplyingAgencyService,
                      HostLmsService hostLmsService,
                      HostLmsReactions hostLmsReactions,
                      PatronRequestWorkflowService patronRequestWorkflowService,
                      ReactorFederatedLockService reactorFederatedLockService,
											RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.hostLmsReactions = hostLmsReactions;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.reactorFederatedLockService = reactorFederatedLockService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

  @Timed("tracking.run")
  @AppTask
  @Override
  @Scheduled(initialDelay = "2m", fixedDelay = "${dcb.tracking.interval:5m}")
  public void run() {
    log.debug("DCB Tracking Service run");

		Flux.from(patronRequestRepository.findScheduledChecks())
			.doOnNext( tracking_record -> log.debug("Scheduled check for {}",tracking_record))
			.flatMap(this::doTracking)
			.transformDeferred(reactorFederatedLockService.withLockOrEmpty(LOCK_NAME))
			.count()
			.subscribe(
				total -> log.info("TRACKING Tracking completed for {} total Requests", total),
				error -> log.error("TRACKING Error when updating tracking information"));
	}

	private <T> Function<Flux<T>, Flux<T>> enrichWithLogging( String successMsg, String errorMsg ) {
		return (source) -> source
			.doOnComplete(() -> log.info(successMsg))
			.doOnError(error -> log.error(errorMsg, error));
	}


	private Mono<PatronRequestRepository.ScheduledTrackingRecord> doTracking(PatronRequestRepository.ScheduledTrackingRecord tr) {
		return Mono.from(patronRequestRepository.findById(tr.id()))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.flatMap(this::trackBorrowingSystem)
			.flatMap(this::trackPickupSystem)
			.flatMap(this::trackSupplyingSystem)
			.flatMap(ctx -> patronRequestWorkflowService.progressUsing(ctx))
			.thenReturn(tr);
	}

	private Mono<RequestWorkflowContext> trackBorrowingSystem(RequestWorkflowContext rwc) {
		log.debug("trackBorrowingSystem");
		return Mono.just(rwc.getPatronRequest())
			.flatMap(this::checkPatronRequest)
			.flatMap(this::checkVirtualItem)
			.thenReturn(rwc);
	}

	private Mono<RequestWorkflowContext> trackPickupSystem(RequestWorkflowContext rwc) {
		log.debug("trackPickupSystem");
		return Mono.just(rwc);
	}
	private Mono<RequestWorkflowContext> trackSupplyingSystem(RequestWorkflowContext rwc) {
		log.debug("trackSupplyingSystem");
		return Mono.just(rwc.getSupplierRequest())
			.flatMap(this::checkSupplierRequest)
			.flatMap(this::checkSupplierItem)
			.thenReturn(rwc);
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<PatronRequest> checkPatronRequest(PatronRequest pr) {
		log.info("TRACKING Check patron request {}", pr);

		// If we dont have an ID, or we previously did have an ID but have detected the downstream is MISSING, bail
		if ( ( pr.getLocalRequestId() == null ) ||
			   ( ( pr.getLocalRequestStatus() != null ) && ( pr.getLocalRequestStatus().equals("MISSING")))) {
			log.warn("No local request ID or status is missing - cannot track");
			return Mono.just(pr);
		}

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

		if ( ( pr.getLocalItemId() == null ) ||
			( (pr.getLocalItemStatus() != null ) &&  (pr.getLocalItemStatus().equals("MISSING")) )){
			log.warn("Unable to track virtual item - no ID");
			return Mono.just(pr);
		}

		log.info("TRACKING Check (local) virtualItem from patron request {} {} {}", pr.getLocalItemId(), pr.getLocalItemStatus(), pr.getPatronHostlmsCode());

		if (pr.getPatronHostlmsCode() != null) {
			return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
				.flatMap(client -> Mono.from(client.getItem(pr.getLocalItemId(), pr.getLocalRequestId())))
				.flatMap( item -> {
					if ( ((item.getStatus() == null) && (pr.getLocalItemStatus() != null)) ||
            ((item.getStatus() != null) && (pr.getLocalItemStatus() == null)) ||
            (!item.getStatus().equals(pr.getLocalItemStatus()))) {
						// Item status has changed - so issue an update
	          log.debug("TRACKING Detected borrowing system - virtual item status change {} to {}", pr.getLocalItemStatus(), item.getStatus());
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
					}
					else {
						// virtual item status has not changed - just update trackig stats
	          log.debug("TRACKING - update virtual item repeat counter {} {} {}",pr.getId(), pr.getLocalItemStatus(), pr.getLocalItemStatusRepeat());
		        return Mono.from(patronRequestRepository.updateLocalItemTracking(pr.getId(), pr.getLocalItemStatus(), Instant.now(),
			          incrementRepeatCounter(pr.getLocalItemStatusRepeat())))
				      .doOnNext(count -> log.debug("update count {}",count))
					    .thenReturn(pr);
					}
			
				});
		}
		else {
			log.warn("Trackable local item - NULL");
			return Mono.just(pr);
		}
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<SupplierRequest> checkSupplierItem(SupplierRequest sr) {

		if ( ( sr.getLocalItemId() == null ) ||
			( (sr.getLocalItemStatus() != null ) && ( sr.getLocalStatus().equals("MISSING")) ) ) {
			log.warn("unable to track supplier item - no ID");
			return Mono.just(sr);
		}

		log.info("TRACKING Check (hostlms) supplierItem from supplier request item={} status={} code={}",
			sr.getLocalItemId(), sr.getLocalItemStatus(), sr.getHostLmsCode());

		if (sr.getHostLmsCode() != null) {
			log.debug("TRACKING hostLms code and itemId present.. continue");

			return hostLmsService.getClientFor(sr.getHostLmsCode())
				.flatMap(client -> Mono.from(client.getItem(sr.getLocalItemId(), sr.getLocalId())))
				.doOnNext(item -> log.debug("Process tracking supplier request item {}", item))
        .flatMap( item -> {
					if ( ((item.getStatus() == null) && (sr.getLocalItemStatus() != null)) ||     // remote item status is null, local is not
            ((item.getStatus() != null) && (sr.getLocalItemStatus() == null)) ||			  // remote status is null local is null
            (!item.getStatus().equals(sr.getLocalItemStatus()))) {	                    // remote != local

						log.debug("Detected supplying system - supplier item status change {} to {}", sr.getLocalItemStatus(), item.getStatus());
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
          }
          else {
            log.debug("TRACKING - update supplier item counter {} {} {}",sr.getId(), sr.getLocalItemStatus(), sr.getLocalItemStatusRepeat());
						// ToDo - add required methods to supplier repo
            return Mono.from(supplierRequestRepository.updateLocalItemTracking(sr.getId(), sr.getLocalItemStatus(), Instant.now(),
                incrementRepeatCounter(sr.getLocalItemStatusRepeat())))
              .doOnNext(count -> log.debug("update count {}",count))
              .thenReturn(sr);
          }
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

		if ( ( sr.getLocalId() == null ) ||
			( ( sr.getLocalStatus() != null ) && ( sr.getLocalStatus().equals("MISSING")))) {
			log.warn("unable to track supplier request - no ID");
			return Mono.just(sr);
		}

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
			.flatMap( hold -> {
				if ( !hold.getStatus().equals(sr.getLocalStatus()) ) {
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
				}
				else {
					log.debug("TRACKING - update supplier request counter {} {} {}",sr.getId(), sr.getLocalStatus(), sr.getLocalRequestStatusRepeat());
          return Mono.from(supplierRequestRepository.updateLocalRequestTracking(sr.getId(), sr.getLocalStatus(), Instant.now(),
                incrementRepeatCounter(sr.getLocalRequestStatusRepeat())))
            .doOnNext(count -> log.debug("update count {}",count))
            .thenReturn(sr);

				}
			})
			.onErrorResume( error -> Mono.defer(() -> {
				log.error("TRACKING Error occurred: " + error.getMessage(), error);

				if ( ! "ERROR".equals(sr.getLocalStatus()) ) {
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
				}
				else {
					return Mono.just(sr);
				}
			}));
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<PatronRequest> tryToProgressDCBRequest(PatronRequest patronRequest) {
		log.debug("TRACKING Attempt to progress {}:{}",patronRequest.getId(),patronRequest.getStatus());
		return patronRequestWorkflowService.progressAll(patronRequest);
	}

}
