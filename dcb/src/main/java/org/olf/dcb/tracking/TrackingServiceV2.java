package org.olf.dcb.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;
import services.k_int.stability.FeatureGate;
import services.k_int.stability.StabilityLevel.Beta;

@Slf4j
@Singleton
@Beta
@FeatureGate("tracking-v2")
@Replaces(bean = TrackingService.class)
@ExecuteOn(TaskExecutors.BLOCKING)
public class TrackingServiceV2 implements Runnable {
	private static final List<Status> TERMINAL_STATES = List.of(
			Status.ERROR,
			Status.FINALISED,
			Status.COMPLETED);
	
	
	public static final String LOCK_NAME = "tracking-service";
	private final PatronRequestRepository patronRequestRepository;
	private final StatusCodeRepository statusCodeRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplyingAgencyService supplyingAgencyService;
	private final HostLmsService hostLmsService;
	private final HostLmsReactions hostLmsReactions;
	private final PatronRequestWorkflowService patronRequestWorkflowService;
	private final ReactorFederatedLockService reactorFederatedLockService;

	TrackingServiceV2(
			PatronRequestRepository patronRequestRepository,
			SupplierRequestRepository supplierRequestRepository,
			StatusCodeRepository statusCodeRepository,
			SupplyingAgencyService supplyingAgencyService,
			HostLmsService hostLmsService,
			HostLmsReactions hostLmsReactions,
			PatronRequestWorkflowService patronRequestWorkflowService,
			ReactorFederatedLockService reactorFederatedLockService) {

		this.patronRequestRepository = patronRequestRepository;
		this.statusCodeRepository = statusCodeRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.hostLmsReactions = hostLmsReactions;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.reactorFederatedLockService = reactorFederatedLockService;
		
		log.info("---- Tracking V2 active ----");
	}
	
	
	@Transactional(propagation = Propagation.MANDATORY)
  protected Mono<PatronRequest> doBorrowerHoldCheck(Collection<String> dcbRequestCodes, Collection<String> localRequestCodes, PatronRequest patronRequest) {
		return Mono.justOrEmpty(patronRequest)
			.filter( pr -> passBackoffPolling("PatronRequest", pr.getId(), pr.getLocalRequestLastCheckTimestamp(), pr.getLocalRequestStatusRepeat()) )
			.filter( pr -> pr.getLocalRequestStatus() == null || localRequestCodes.contains(pr.getLocalRequestStatus()) )
			.filter( pr -> dcbRequestCodes.contains( pr.getStatus().toString() ))
			.flatMap(this::checkPatronRequest);
	}

	@Transactional(propagation = Propagation.MANDATORY)
  protected Mono<PatronRequest> doBorrowerItemCheck(Collection<String> dcbRequestCodes, Collection<String> localItemCodes, PatronRequest patronRequest) {
		return Mono.justOrEmpty(patronRequest)
			.filter( pr -> passBackoffPolling("VirtualItem", pr.getId(), pr.getLocalItemLastCheckTimestamp(), pr.getLocalItemStatusRepeat()) )
			.filter( pr -> pr.getLocalItemStatus() == null || localItemCodes.contains(pr.getLocalItemStatus()) )
			.filter( pr -> dcbRequestCodes.contains( pr.getStatus().toString() ))
			.flatMap(this::checkVirtualItem);
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
  protected Mono<PatronRequest> doBorrowerChecks( Map<String, Collection<String>> trackableStates, PatronRequest patronRequest ) {
  	
  	// Create a list of viable item status codes. 
  	final Collection<String> localItemCodes = trackableStates.get("VirtualItem");
  	final Collection<String> localRequestCodes = trackableStates.get("PatronRequest");
  	final Collection<String> dcbRequestCodes = trackableStates.get("DCBRequest");
  	
  	return Flux.merge(
				doBorrowerHoldCheck(dcbRequestCodes, localRequestCodes, patronRequest),
				doBorrowerItemCheck(dcbRequestCodes, localItemCodes, patronRequest))
  		.last(patronRequest);
  }
  
  
  @Transactional(propagation = Propagation.MANDATORY)
  protected Mono<SupplierRequest> doSupplierItemCheck( Collection<String> supplierItemCodes, PatronRequest patronRequest, SupplierRequest supplierRequest ) {
  	
  	return Mono.justOrEmpty(supplierRequest)
			.filter( sr -> passBackoffPolling("SupplierItem", sr.getId(), sr.getLocalItemLastCheckTimestamp(), sr.getLocalItemStatusRepeat()) )
			.filter( sr -> sr.getLocalItemStatus() == null || supplierItemCodes.contains(sr.getLocalItemStatus()) )
			.map( sr -> Tuples.of(patronRequest, sr) )
			.flatMap( TupleUtils.function(this::checkSupplierItem));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  protected Mono<SupplierRequest> doSupplierHoldsCheck( Collection<String> supplierRequestCodes, PatronRequest patronRequest, SupplierRequest supplierRequest ) {
		return Mono.justOrEmpty(supplierRequest)
			.filter( sr -> passBackoffPolling("SupplierRequest", sr.getId(), sr.getLocalRequestLastCheckTimestamp(), sr.getLocalRequestStatusRepeat()) )
			.filter( sr -> sr.getLocalItemStatus() == null || supplierRequestCodes.contains(sr.getLocalItemStatus()) )
			.map( sr -> Tuples.of(patronRequest, sr) )
			.flatMap(TupleUtils.function(this::checkSupplierRequest));
	}
	
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected Mono<PatronRequest> doSupplierChecks( Map<String, Collection<String>> trackableStates, PatronRequest pr ) {
  	  	
  	// Create a list of viable item status codes. 
  	final Collection<String> supplierItemCodes = trackableStates.get("SupplierItem");
  	final Collection<String> supplierRequestCodes = trackableStates.get("SupplierRequest");
  	
  	// Handle all the attached supplier requests.
  	return Mono.justOrEmpty(pr.getSupplierRequests())
  		.flatMapMany(Flux::fromIterable)
  		.flatMap( sr -> Flux.merge(
					doSupplierItemCheck(supplierItemCodes, pr, sr),
					doSupplierHoldsCheck(supplierRequestCodes, pr, sr)))
  		.then( Mono.just(pr) );
  }


  @Transactional(propagation = Propagation.MANDATORY)
	protected Mono<PatronRequest> doSupplierAndBorrowerChecks(Map<String, Collection<String>> trackableStates, PatronRequest pr) {
		// Merge the 2 paths.
		return Flux.concat(
				doSupplierChecks( trackableStates, pr ),
				doBorrowerChecks( trackableStates, pr )) // Eager subscription to both sources.
			.last(pr); // Should always be the same PatronRequest 
	}

  @Transactional(propagation = Propagation.MANDATORY)
	protected Flux<PatronRequest> trackUsingStates ( List<StatusCode> trackableStates ) {
  	
  	return Flux.fromIterable( trackableStates )
  		.collectMultimap(StatusCode::getModel, StatusCode::getCode)
  		.flatMapMany( stateMap -> { // Map of Model -> Code
  			log.atDebug()
  				.log("Trackable states: {}", stateMap);
  				
  			Set<String> allCodes = new HashSet<>();
  			stateMap.forEach( (type, vals) -> vals.forEach(allCodes::add) );
  			return Flux.from( patronRequestRepository.findAllTrackableRequests(TERMINAL_STATES, allCodes, allCodes) )
  				.flatMap(pr -> doSupplierAndBorrowerChecks( stateMap, pr ));
  		});
//  	
//  	
//		return Mono.just(trackableStates.stream()
//				.map(StatusCode::getCode)
//				.collect(Collectors.toUnmodifiableSet()))
//			// Grab the list of candidates.
//			.flatMapMany(stateCodes -> patronRequestRepository.findAllTrackableRequests(TERMINAL_STATES, stateCodes, stateCodes))
//			.flatMap(pr -> doSupplierAndBorrowerChecks( trackableStates, pr ));
	}
	
	@Transactional(readOnly = true)
	protected Flux<PatronRequest> doRemoteChecks() {
		return Flux.from(statusCodeRepository.findAllByTracked(true))
			.collectList()
			.flatMapMany(this::trackUsingStates);
	}
	
	@AppTask
	@Override
	@Scheduled(initialDelay = "20s", fixedDelay = "${dcb.tracking.interval:5m}")
	public void run() {
		
		// Start by finding the list of tracked status codes.
		doRemoteChecks()
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

	@Transactional(readOnly = true)
	protected Flux<PatronRequest> trackActiveDCBRequests() {
		return Flux.from(patronRequestRepository.findProgressibleDCBRequests())
			.doOnSubscribe(_s -> log.info("TRACKING trackActiveDCBRequests()"))
			.concatMap(this::tryToProgressDCBRequest)
			.transform(enrichWithLogging("TRACKING active DCB request tracking complete", "TrackingError (DCBRequest):"));
	}

	private <T> Function<Flux<T>, Flux<T>> enrichWithLogging( String successMsg, String errorMsg ) {
		return (source) -> source
			.doOnComplete(() -> log.info(successMsg))
			.doOnError(error -> log.error(errorMsg, error));
	}

	/**
	 * This method is used to provide backoff period for polling.. In the first moments after a change has been made we want to poll
   * pretty persistently - because users in testing will be changing things and expecting to see a result. As time goes on however,
   * it is not sensible to poll every request constantly so we back off
   *
	 */
	private boolean passBackoffPolling(String tp, UUID id, Instant lastCheck, Long repeatCount) {
		boolean result = true;

		long rc = repeatCount != null ? repeatCount.longValue() : 0;
		if ( lastCheck == null )
			rc = 0;

		// First 10 poll immediately, otherwise
		if ( rc > 10 ) {
			Duration duration_since_last_check = Duration.between(lastCheck, Instant.now());
			long seconds_since_last_check = duration_since_last_check.getSeconds();
			if ( rc < 20 ) {
				// Next 10 - wait 10 mins before asking again
				if ( seconds_since_last_check < 600 )
					return false;
			}
			if ( seconds_since_last_check < 5400 )
				return false;  // At least check every 90m
		}
		log.debug("passBackoffPolling({},{},{},{}:{})",tp,id,lastCheck,repeatCount,result);
		return result;
	}

	// The check methods themselves

	@Transactional(propagation = Propagation.MANDATORY)
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

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<PatronRequest> checkVirtualItem(PatronRequest pr) {

		log.info("TRACKING Check (local) virtualItem from patron request {} {} {}", pr.getLocalItemId(), pr.getLocalItemStatus(), pr.getPatronHostlmsCode());

		if (pr.getPatronHostlmsCode() != null) {
			return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
				.flatMap(client -> client.getItem(pr.getLocalItemId(), pr.getLocalRequestId()))
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

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<SupplierRequest> checkSupplierItem(PatronRequest pr, SupplierRequest sr) {
		
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
              .patronRequestId(pr.getId())
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

	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<SupplierRequest> checkSupplierRequest(PatronRequest pr, SupplierRequest sr) {
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
		        .patronRequestId(pr.getId())
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
  	        .patronRequestId(pr.getId())
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

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Flux<PatronRequest> tryToProgressDCBRequest(PatronRequest patronRequest) {
		log.debug("TRACKING Attempt to progress {}:{}",patronRequest.getId(),patronRequest.getStatus());
		return patronRequestWorkflowService.progressAll(patronRequest);
	}
}
