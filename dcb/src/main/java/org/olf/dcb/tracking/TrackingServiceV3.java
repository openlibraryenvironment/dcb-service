package org.olf.dcb.tracking;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.tracking.StateChangeFactory.SUPPLIER_REQUEST_ERROR;
import static org.olf.dcb.tracking.StateChangeFactory.patronRequestStatusChanged;
import static org.olf.dcb.tracking.StateChangeFactory.pickupItemStatusChanged;
import static org.olf.dcb.tracking.StateChangeFactory.pickupRequestStatusChanged;
import static org.olf.dcb.tracking.StateChangeFactory.supplierItemStatusChanged;
import static org.olf.dcb.tracking.StateChangeFactory.supplierRequestErrored;
import static org.olf.dcb.tracking.StateChangeFactory.supplierRequestStatusChanged;
import static org.olf.dcb.tracking.StateChangeFactory.virtualItemRenewalCountChanged;
import static org.olf.dcb.tracking.StateChangeFactory.virtualItemStatusChanged;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micrometer.core.annotation.Timed;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;

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
	private final PatronRequestAuditService patronRequestAuditService;

	@Value("${dcb.tracking.dryRun:false}")
	private Boolean dryRun;

  private Duration lastTrackingRunDuration;
  private Long lastTrackingRunCount;

	private final int MAX_TRACKING_CONCURRENCY = 10;
  // If a request in a trackable state gets stuck in a non-termina state for > this number of days.
  // mark it "TooLong" which will stop it being visited by the tracking code.
	private final int TOO_LONG_THRESHOLD = 56;

	TrackingServiceV3(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		SupplyingAgencyService supplyingAgencyService,
		HostLmsService hostLmsService,
		HostLmsReactions hostLmsReactions,
		PatronRequestWorkflowService patronRequestWorkflowService,
		ReactorFederatedLockService reactorFederatedLockService,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.hostLmsService = hostLmsService;
		this.hostLmsReactions = hostLmsReactions;
		this.patronRequestWorkflowService = patronRequestWorkflowService;
		this.reactorFederatedLockService = reactorFederatedLockService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Timed("tracking.run")
	@AppTask
	@Override
	@Scheduled(initialDelay = "2m", fixedDelay = "${dcb.tracking.interval:5m}")
	public void run() {
		log.debug("DCB Tracking Service run");
    Instant start = Instant.now(); // ⏱ Start timing

		Flux.from(patronRequestRepository.findScheduledChecks())
			.doOnNext( tracking_record -> log.debug("Scheduled check for {}",tracking_record))
			.flatMap(this::doTracking, MAX_TRACKING_CONCURRENCY)
			.transformDeferred(reactorFederatedLockService.withLockOrEmpty(LOCK_NAME))
			.count()
      .doOnSuccess(total -> {
        this.lastTrackingRunDuration = Duration.between(start, Instant.now()); // ⏱ Store duration
        this.lastTrackingRunCount = total;
        log.info("TRACKING Tracking completed for {} total Requests in {}", total, lastTrackingRunDuration);
      })
      .doOnError(error -> {
        this.lastTrackingRunDuration = Duration.between(start, Instant.now()); // ⏱ Even on error
        this.lastTrackingRunCount = Long.valueOf(0);
        log.error("TRACKING Error {} when updating tracking information in {}", error.getMessage(), lastTrackingRunDuration, error);
      })
			.subscribe(
				total -> log.info("TRACKING Tracking completed for {} total Requests", total),
				error -> log.error("TRACKING Error when updating tracking information", error));
	}

	private <T> Function<Flux<T>, Flux<T>> enrichWithLogging( String successMsg, String errorMsg ) {
		return (source) -> source
			.doOnComplete(() -> log.info(successMsg))
			.doOnError(error -> log.error("TrackingEnrichedError: "+errorMsg, error));
	}

	private Mono<RequestWorkflowContext> auditTrackingError(
		String message, RequestWorkflowContext ctx, Throwable error) {
		final var auditData = new HashMap<String, Object>();
		auditThrowable(auditData, "Throwable", error);
		return patronRequestAuditService.auditTrackingError(message, ctx.getPatronRequest(), auditData)
			.flatMap(audit -> Mono.just(ctx)); // Resume tracking after auditing
	}

	private Mono<PatronRequest> auditPatronRequestTrackingError(
		String message, PatronRequest patronRequest, Throwable error) {

		final var auditData = new HashMap<String, Object>();
		auditThrowable(auditData, "Throwable", error);

		log.error("TRACKING Error occurred tracking patron request in local system {}", patronRequest.getId());

		return patronRequestAuditService.auditTrackingError(message, patronRequest, auditData)
			.flatMap(audit -> Mono.just(patronRequest)); // Resume tracking after auditing
	}

	private Mono<PatronRequest> auditVirtualItemTrackingError(
		String message, PatronRequest patronRequest, Throwable error) {

		final var auditData = new HashMap<String, Object>();
		auditThrowable(auditData, "Throwable", error);

		log.error("TRACKING Error occurred tracking virtual item in local system {}", patronRequest.getId());

		return patronRequestAuditService.auditTrackingError(message, patronRequest, auditData)
			.flatMap(audit -> Mono.just(patronRequest)); // Resume tracking after auditing
	}

	public RequestWorkflowContext incrementManualPollCounter(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().incrementManualPollCountForCurrentStatus();
		return ctx;
	}

	public RequestWorkflowContext incrementAutoPollCounter(RequestWorkflowContext ctx) {
		ctx.getPatronRequest().incrementAutoPollCountForCurrentStatus();
		return ctx;
	}

	/**
	 * Force an update of the patron request.
	 * @param pr_id ID of the patron request
	 * @return patron request
	 */
	public Mono<UUID> forceUpdate(UUID pr_id) {
		log.debug("Manual tracking poll for patron request \"{}\"", pr_id);

		return fetchWorkflowContext(pr_id)
			.flatMap(ctx -> failSafeTracking(ctx, false))
			.flatMap(ctx -> Mono.just(pr_id))
			.doOnError(error -> log.error("TRACKING ForceUpdate Error caught {}", pr_id, error))
			.onErrorResume(error -> Mono.just(pr_id));
	}

	/**
	 * doTracking.
	 * We may choose to loop back to the HTTP forceUpdate request. This would enable us to use a load balancer
	 * to spread the load of the tracking phase over a cluster of dcb-service instances. Alternatively we could
	 * use a hazelcast distributed queue.
	 * @param tr
	 * @return
	 */
	private Mono<PatronRequestRepository.ScheduledTrackingRecord> doTracking(
		PatronRequestRepository.ScheduledTrackingRecord tr) {

		return fetchWorkflowContext(tr.id())
			.flatMap(ctx -> failSafeTracking(ctx, true))
			.thenReturn(tr)
			.doOnError(error -> log.error("TRACKING doTracking Error caught {}",
				getValue(tr, PatronRequestRepository.ScheduledTrackingRecord::id, "Unable to get tr.id()"), error))
			.onErrorResume(error ->  Mono.just(tr));
	}

	private Mono<RequestWorkflowContext> failSafeTracking(RequestWorkflowContext ctx, boolean isAutoTracking) {
		final var pr_id = ctx.getPatronRequest().getId();

    Instant lastStateChange = ctx.getPatronRequest().getCurrentStatusTimestamp();
    if ( lastStateChange != null ) {
      Instant tooLongThreshold = Instant.now().minus(Duration.ofDays(TOO_LONG_THRESHOLD));
      if ( lastStateChange.isBefore(tooLongThreshold) ) {
        return tooLongHandling(ctx);
      }
    }

		return Mono.just(isAutoTracking ? incrementAutoPollCounter(ctx) : incrementManualPollCounter(ctx))
			.flatMap(context -> isAutoTracking ? trackSystems(context) : manuallyTrack(context))
			.doOnError(error -> log.error("TRACKING Error occurred tracking patron request in local systems {}", pr_id, error))
			.flatMap(context -> {
				if (Boolean.TRUE.equals(dryRun)) {
					log.info("DRY RUN: Skipping progressUsing {}", context);
						return Mono.just(context);
	        }
		      return patronRequestWorkflowService.progressUsing(context);
			  })
			.doOnError(error -> log.error("TRACKING Error occurred progressing patron request {}", pr_id, error))
			.onErrorResume(error -> Mono.just(ctx));
	}

	private Mono<RequestWorkflowContext> tooLongHandling(RequestWorkflowContext ctx) {
    log.warn("Patron request selected for too long handling {}",ctx.getPatronRequest().getId());
    
    return Mono.from(patronRequestRepository.updateIsTooLongAndNeedsAttention(ctx.getPatronRequest().getId(), Boolean.TRUE, Boolean.TRUE))
      .thenReturn(ctx);
  }

	private Mono<RequestWorkflowContext> fetchWorkflowContext(UUID pr_id) {
		return Mono.from(patronRequestRepository.findById(pr_id))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.doOnError(error -> log.error("TRACKING Error occurred finding patron request (or context) {}", pr_id, error));
	}

	private Mono<RequestWorkflowContext> manuallyTrack(RequestWorkflowContext ctx) {
		return patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), "Manual update actioned.")
			.flatMap(audit -> trackSystems(ctx));
	}

	private <R> Mono<RequestWorkflowContext> trackSystems(RequestWorkflowContext ctx) {
		return this.trackBorrowingSystem(ctx)
			.onErrorResume(error -> auditTrackingError("Tracking failed : Borrowing System", ctx, error))
			.flatMap(this::trackPickupSystem)
			.onErrorResume(error -> auditTrackingError("Tracking failed : Pickup System", ctx, error))
			.flatMap(this::trackSupplyingSystem)
			.onErrorResume(error -> auditTrackingError("Tracking failed : Supplying System", ctx, error));
	}

	private Mono<RequestWorkflowContext> trackBorrowingSystem(RequestWorkflowContext rwc) {

		final var state = rwc.getPatronRequest().getStatus();

		if (isPrematureTracking(state)) {

			log.warn("PR {} in state {} skipped tracking of borrowing system", rwc.getPatronRequest().getId(), state);

			return skipTracking(rwc,"Tracking skipped : Borrowing System", "Cannot track PatronRequest in status " + state);

		} else {

			log.debug("trackBorrowingSystem");

			final var patronRequest = rwc.getPatronRequest();

			return Mono.just(patronRequest)
				.flatMap(this::checkPatronRequest)
				// handle any errors here so we don't skip getting the virtual item updates
				.onErrorResume(error -> auditPatronRequestTrackingError("Tracking failed : Patron Request", patronRequest, error))
				.flatMap(this::checkVirtualItem)
				.onErrorResume(error -> auditVirtualItemTrackingError("Tracking failed : Virtual Item", patronRequest, error))
				.thenReturn(rwc);
		}
	}

	private Mono<RequestWorkflowContext> skipTracking(RequestWorkflowContext rwc, String message, String reason) {
		final var auditData = new HashMap<String, Object>();
		auditData.put("Reason", reason);

		return patronRequestAuditService
			.addAuditEntry(rwc.getPatronRequest(), message, auditData)
			.thenReturn(rwc);
	}

	private boolean isPrematureTracking(PatronRequest.Status status) {
		return CONFIRMED.equals(status) || REQUEST_PLACED_AT_SUPPLYING_AGENCY.equals(status);
	}

	private Mono<RequestWorkflowContext> trackPickupSystem(RequestWorkflowContext rwc) {
		log.debug("trackPickupSystem");

		if ( !"RET-PUA".equals(rwc.getPatronRequest().getActiveWorkflow() )) {
			log.warn("PR {} not RET-PUA skipped tracking of pickup system", rwc.getPatronRequest().getId());
			return Mono.just(rwc);
		}

		final var state = rwc.getPatronRequest().getStatus();
		final var SKIPPED_STATUSES = List.of(CONFIRMED, REQUEST_PLACED_AT_SUPPLYING_AGENCY, REQUEST_PLACED_AT_BORROWING_AGENCY);

		if ( SKIPPED_STATUSES.contains(state)) {
			log.warn("PR {} in state {} skipped tracking of pickup system", rwc.getPatronRequest().getId(), state);
			return skipTracking(rwc, "Tracking skipped : Pickup System", "Cannot track PickupRequest in status " + state);
		}

		return checkPickupRequest(rwc)
			.flatMap(this::checkPickupItem);
	}

	private Mono<RequestWorkflowContext> trackSupplyingSystem(RequestWorkflowContext rwc) {
		log.debug("trackSupplyingSystem prId={}",rwc.getPatronRequest().getId());
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

		final var requestId = getValueOrNull(pr, PatronRequest::getLocalRequestId);
		// assuming the identity is attached when the context is built
		final var localPatronId = getValueOrNull(pr, PatronRequest::getRequestingIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(requestId).localPatronId(localPatronId).build();

		return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
			.flatMap(client -> client.getRequest(hostlmsRequest))
			.doOnError(e -> log.error("Tracking Error occurred: {}", e.getMessage(), e))
			.doOnNext(hold -> log.info("TRACKING Compare patron request {} states: {} and {}", pr.getId(), hold.getStatus(), pr.getLocalRequestStatus()))
			.flatMap( hold -> {
				if ( hold.getStatus().equals(pr.getLocalRequestStatus()) ) {
					// The hold status is the same as the last time we checked - update the tracking info and return
					log.debug("TRACKING - update PR repeat counter {} {} {}",pr.getId(), pr.getLocalRequestStatus(), pr.getLocalRequestStatusRepeat());
					return Mono.from(patronRequestRepository.updateLocalRequestTracking(pr.getId(), pr.getLocalRequestStatus(), hold.getRawStatus(), Instant.now(),
							incrementRepeatCounter(pr.getLocalRequestStatusRepeat())))
						.doOnNext(count -> log.debug("update count {}",count))
						.thenReturn(pr);
				}
				else {
					// The hold status has changed - do something different
					StateChange sc = patronRequestStatusChanged(pr, hold);

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

		// Please note: some HostLms don't track item statuses by item id so the item id can be null
		if (( (pr.getLocalItemStatus() != null ) && (pr.getLocalItemStatus().equals("MISSING")) )) {
			log.warn("TRACKING Unable to track virtual item for pr {} - no ID {} {}",pr.getId(),pr.getLocalItemId(),pr.getLocalItemStatus());
			return Mono.just(pr);
		}

		log.info("TRACKING Check (local) virtualItem from patron request {} {} {}", pr.getLocalItemId(), pr.getLocalItemStatus(), pr.getPatronHostlmsCode());

		if (pr.getPatronHostlmsCode() != null) {

			final var hostLmsItem = HostLmsItem.builder()
				.localId(pr.getLocalItemId())
				.localRequestId(pr.getLocalRequestId())
				.holdingId(getValueOrNull(pr, PatronRequest::getLocalHoldingId))
				.bibId(getValueOrNull(pr, PatronRequest::getLocalBibId))
				.build();

			return hostLmsService.getClientFor(pr.getPatronHostlmsCode())
				.flatMap(client -> Mono.from(client.getItem(hostLmsItem)))
				.doOnSuccess(
					item1 -> log.debug("TRACKING retrieved virtual item {}", item1))
				.flatMap( item -> {
					if ( ((item.getStatus() == null) && (pr.getLocalItemStatus() != null)) ||
						((item.getStatus() != null) && (pr.getLocalItemStatus() == null)) ||
						(!item.getStatus().equals(pr.getLocalItemStatus()))) {
						// Item status has changed - so issue an update

						log.debug("TRACKING Detected borrowing system - virtual item status change {} to {}", pr.getLocalItemStatus(), item.getStatus());
						StateChange sc = virtualItemStatusChanged(pr, item);

						log.info("TRACKING-EVENT vitem change event {}", sc);

						return hostLmsReactions.onTrackingEvent(sc)
							.thenReturn(pr);
					}
					else if (item.getRenewalCount() != null && 														// tracked item has a non-null renewal count
						!Objects.equals(item.getRenewalCount(), pr.getLocalRenewalCount())) // and the renewal count has changed
					{
						// Item renewal count has changed - so issue an update
						log.debug("TRACKING Detected borrowing system - virtual item renewal count change {} to {}", pr.getLocalRenewalCount(), item.getRenewalCount());
						StateChange sc = virtualItemRenewalCountChanged(pr, item);
						log.info("TRACKING-EVENT vitem change event {}", sc);
						return hostLmsReactions.onTrackingEvent(sc)
							.thenReturn(pr);
					}
					else {
						// virtual item status or renewal count has not changed - just update tracking stats
						log.debug("TRACKING - update virtual item repeat counter {} {} {}",pr.getId(), pr.getLocalItemStatus(), pr.getLocalItemStatusRepeat());
						return Mono.from(patronRequestRepository.updateLocalItemTracking(pr.getId(), pr.getLocalItemStatus(), item.getRawStatus(), Instant.now(),
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

  private boolean hasValueChanged(Object o1, Object o2) {
    return (
      ((o1 == null) && ( o2 != null)) ||
      ((o1 != null) && ( o2 == null)) ||
      (! o1.equals(o2) )
    );
  }

  private boolean hasSupplierItemChanged(SupplierRequest sr, HostLmsItem item) {
    return ( 
      hasValueChanged(item.getStatus(), sr.getLocalItemStatus() ) ||
      hasValueChanged(item.getRenewable(), sr.getLocalRenewable() ) ||
      hasValueChanged(item.getRenewalCount(), sr.getLocalRenewalCount() )
    );
  }


	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<SupplierRequest> checkSupplierItem(SupplierRequest sr) {

		if ( ( sr.getLocalItemId() == null ) ||
			( (sr.getLocalItemStatus() != null ) && ( sr.getLocalItemStatus().equals("MISSING")) ) ) {
			log.warn("unable to track supplier item - no ID {} {}",sr.getLocalItemId(),sr.getLocalItemStatus());
			return Mono.just(sr);
		}

		log.info("TRACKING Check (hostlms) supplierItem from supplier request item={} status={} code={}",
			sr.getLocalItemId(), sr.getLocalItemStatus(), sr.getHostLmsCode());

		if (sr.getHostLmsCode() != null) {
			log.debug("TRACKING hostLms code and itemId present.. continue");

			final var hostLmsItem = HostLmsItem.builder()
				.localId(sr.getLocalItemId())
				.localRequestId(sr.getLocalId())
				.holdingId(getValueOrNull(sr, SupplierRequest::getLocalHoldingId))
				.bibId(getValueOrNull(sr, SupplierRequest::getLocalBibId))
				.build();

			return hostLmsService.getClientFor(sr.getHostLmsCode())
				.flatMap(client -> Mono.from(client.getItem(hostLmsItem)))
				.doOnNext(item -> log.debug("Process tracking supplier request item {}", item))
				.flatMap( item -> {
					// *Ian: Surely the next else block here is better dealt with by a new || condition here?
					// I can't see any behaviour different if the condition matches?*
          //
					if ( hasSupplierItemChanged(sr, item) ) {
						log.debug("TRACKING Detected supplying system - supplier item status change {} to {}", sr.getLocalItemStatus(), item.getStatus());
						StateChange sc = supplierItemStatusChanged(sr, item);
						log.info("TRACKING-EVENT supplier-item state change {}", sc);
						return hostLmsReactions.onTrackingEvent(sc)
							.thenReturn(sr);
					}
					else {
						log.debug("TRACKING - update supplier item counter {} {} {}",sr.getId(), sr.getLocalItemStatus(), sr.getLocalItemStatusRepeat());
						// ToDo - add required methods to supplier repo
						return Mono.from(supplierRequestRepository.updateLocalItemTracking(sr.getId(), sr.getLocalItemStatus(), item.getRawStatus(), Instant.now(),
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

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<SupplierRequest> checkSupplierRequest(SupplierRequest sr) {

		if ( ( sr.getLocalId() == null ) ||
			( ( sr.getLocalStatus() != null ) && ( sr.getLocalStatus().equals("MISSING")))) {
			log.warn("unable to track supplier request - no ID");
			return Mono.just(sr);
		}

		log.info("TRACKING Check supplier request {} with local status \"{}\"", sr.getId(), sr.getLocalStatus());

		final var localRequestId = sr.getLocalId();
		final var supplierPatronId = getValueOrNull(sr, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(localRequestId).localPatronId(supplierPatronId).build();

		// We fetch the state of the hold at the supplying library. If it is different to the last state
		// we stashed in SupplierRequest.localStatus then we have detected a change. We emit an event to let
		// observers know that the state has changed but we DO NOT directly update localStatus. the handler
		// will arrange for localStatus to be updated once it's action has completed successfully. This means
		// that if a handler fails to complete, on the next iteration this event will fire again, giving us a
		// means to try and recover from failure scenarios. For example, when we detect that a supplying system
		// has changed to "InTransit" the handler needs to update the pickup site and the patron site, if either of
		// these operations fail, we don't update the state - which will cause the handler to re-fire until
		// successful completion.
		return supplyingAgencyService.getRequest(sr.getHostLmsCode(), hostlmsRequest)
			.flatMap( hold -> {
				if ( !hold.getStatus().equals(sr.getLocalStatus()) ) {
					log.debug("TRACKING current request status: {}", hold);

					StateChange sc = supplierRequestStatusChanged(sr, hold);

					log.info("TRACKING Publishing state change event for supplier request {}", sc);

					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(sr);
				}
				else {
					log.debug("TRACKING - update supplier request counter {} {} {}",sr.getId(), sr.getLocalStatus(), sr.getLocalRequestStatusRepeat());
					return Mono.from(supplierRequestRepository.updateLocalRequestTracking(sr.getId(), sr.getLocalStatus(), hold.getRawStatus(), Instant.now(),
								incrementRepeatCounter(sr.getLocalRequestStatusRepeat())))
						.doOnNext(count -> log.debug("update count {}",count))
						.thenReturn(sr);

				}
			})
			.onErrorResume( error -> Mono.defer(() -> {
				log.error("TRACKING Error occurred: " + error.getMessage(), error);

				if ( ! SUPPLIER_REQUEST_ERROR.equals(sr.getLocalStatus()) ) {

					StateChange sc = supplierRequestErrored(sr, error);

					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(sr);
				}
				else {
					return Mono.just(sr);
				}
			}));
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<RequestWorkflowContext> checkPickupRequest(RequestWorkflowContext rwc) {
		final var pr = rwc.getPatronRequest();
		log.info("TRACKING Check pickup request {}", pr);

		// If we dont have an ID, or we previously did have an ID but have detected the downstream is MISSING, bail
		if ( ( pr.getPickupRequestId() == null ) ||
			( ( pr.getPickupRequestStatus() != null ) && ( pr.getPickupRequestStatus().equals("MISSING")))) {
			log.warn("No pickup request ID or status is missing - cannot track");
			return Mono.just(rwc);
		}

		final var requestId = getValueOrNull(pr, PatronRequest::getPickupRequestId);
		final var pickupPatronId = getValueOrNull(rwc, RequestWorkflowContext::getPickupPatronIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(requestId).localPatronId(pickupPatronId).build();

		return hostLmsService.getClientFor(rwc.getPickupSystemCode())
			.flatMap(client -> client.getRequest(hostlmsRequest))
			.onErrorContinue((e, o) -> log.error("Error occurred: " + e.getMessage(), e))
			.doOnNext(hold -> log.info("TRACKING Compare pickup request {} states: {} and {}", pr.getPickupRequestId(), hold.getStatus(), pr.getPickupRequestStatus()))
			.flatMap( hold -> {
				if ( hold.getStatus().equals(pr.getPickupRequestStatus()) ) {
					// The hold status is the same as the last time we checked - update the tracking info and return
					log.debug("TRACKING - update PR repeat counter {} {} {}",pr.getId(), pr.getLocalRequestStatus(), pr.getLocalRequestStatusRepeat());
					return Mono.from(patronRequestRepository.updatePickupRequestTracking(pr.getId(), pr.getPickupRequestStatus(), hold.getRawStatus(), Instant.now(),
							incrementRepeatCounter(pr.getPickupRequestStatusRepeat())))
						.doOnNext(count -> log.debug("update count {}",count))
						.thenReturn(rwc);
				}
				else {
					// The hold status has changed - do something different
					StateChange sc = pickupRequestStatusChanged(pr, hold);

					log.info("TRACKING-EVENT PickupReq state change event {}",sc);
					return hostLmsReactions.onTrackingEvent(sc)
						.thenReturn(rwc);
				}
			});
	}

	@Transactional(Transactional.TxType.REQUIRES_NEW)
	protected Mono<RequestWorkflowContext> checkPickupItem(RequestWorkflowContext rwc) {
		final var pr = rwc.getPatronRequest();

		// Please note: some HostLms don't track item statuses by item id so the item id can be null
		if (( (pr.getPickupItemStatus() != null ) && (pr.getPickupItemStatus().equals("MISSING")) )) {
			log.warn("TRACKING Unable to track pickup item for pr {} - no ID {} {}",pr.getId(),pr.getPickupItemId(),pr.getPickupItemStatus());
			return Mono.just(rwc);
		}

		log.info("TRACKING Check (local) pickupItem from patron request {} {} {}", pr.getPickupItemId(), pr.getPickupItemStatus(), rwc.getPickupSystemCode());

		final var hostLmsItem = HostLmsItem.builder()
			.localId(pr.getPickupItemId())
			.localRequestId(pr.getPickupRequestId())
			.holdingId(getValueOrNull(pr, PatronRequest::getPickupHoldingId))
			.bibId(getValueOrNull(pr, PatronRequest::getPickupBibId))
			.build();

		if (rwc.getPickupSystemCode() != null) {
			return hostLmsService.getClientFor(rwc.getPickupSystemCode())
				.flatMap(client -> Mono.from(client.getItem(hostLmsItem)))
				.flatMap( item -> {
					if ( ((item.getStatus() == null) && (pr.getPickupItemStatus() != null)) ||
						((item.getStatus() != null) && (pr.getPickupItemStatus() == null)) ||
						(!item.getStatus().equals(pr.getPickupItemStatus()))) {
						// Item status has changed - so issue an update

						log.debug("TRACKING Detected pickup system - pickup item status change {} to {}", pr.getPickupItemStatus(), item.getStatus());

						StateChange sc = pickupItemStatusChanged(pr, item);

						log.info("TRACKING-EVENT pickup item change event {}", sc);

						return hostLmsReactions.onTrackingEvent(sc)
							.thenReturn(rwc);
					}
					else {
						// pickup item status has not changed - just update tracking stats
						log.debug("TRACKING - update pickup item repeat counter {} {} {}",pr.getId(), pr.getPickupItemStatus(), pr.getPickupItemStatusRepeat());
						return Mono.from(patronRequestRepository.updatePickupItemTracking(pr.getId(), pr.getPickupItemStatus(), item.getRawStatus(), Instant.now(),
								incrementRepeatCounter(pr.getPickupItemStatusRepeat())))
							.doOnNext(count -> log.debug("update count {}",count))
							.thenReturn(rwc);
					}

				});
		}
		else {
			log.warn("Trackable pickup item - NULL");
			return Mono.just(rwc);
		}
	}

  @Override
  public Duration getLastTrackingRunDuration() {
    return this.lastTrackingRunDuration;
  }

  @Override
  public Long getLastTrackingRunCount() {
    return this.lastTrackingRunCount;
  }
}
