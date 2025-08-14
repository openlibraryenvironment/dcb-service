package org.olf.dcb.tracking;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.zalando.problem.Problem;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 * The majority of the heavy lifting is now done in TrackingServiceV3 - the call 
 *   return patronRequestWorkflowService.progressUsing(context);
 * Will use the objects set up in the context to try and apply workflow steps until no more automated steps can be taken.
 */
@Slf4j
@Singleton
public class HostLmsReactions {
	private final PatronRequestAuditService patronRequestAuditService;
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;

	// Ensure that we have loaded and initialised all workflow actions
	// private final List<WorkflowAction> allWorkflowActions;

	public HostLmsReactions(PatronRequestAuditService patronRequestAuditService,
		// List<WorkflowAction> allWorkflowActions,
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository) {

		// this.allWorkflowActions = allWorkflowActions;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@jakarta.annotation.PostConstruct
	private void init() {
		log.info("HostLmsReactions::init");
		// for (WorkflowAction w : allWorkflowActions) {
		//	log.info("Workflow action: {}", w);
		// }
	}

	@Transactional
	public Mono<Map<String,Object>> onTrackingEvent(TrackingRecord trackingRecord) {
		log.debug("onTrackingEvent {}", trackingRecord);
		// This no longer does anything other than update the relevant state of the items against the PR or SR

		Map<String,Object> context = new HashMap<>();

		if (trackingRecord.getTrackingRecordType().equals(StateChange.STATE_CHANGE_RECORD)) {
			StateChange sc = (StateChange) trackingRecord;
			context.put("StateChange", sc);

			switch (sc.getResourceType()) {
				case "SupplierRequest":
					SupplierRequest sr = (SupplierRequest) sc.getResource();
					sr.setLocalStatus(sc.getToState());
					sr.setLocalRequestLastCheckTimestamp(Instant.now());
					sr.setLocalRequestStatusRepeat(Long.valueOf(0));
					return Mono.from(supplierRequestRepository.update(sr)).flatMap( ssr -> auditEventIndication( context, trackingRecord));
				case "PatronRequest":
					PatronRequest pr = (PatronRequest) sc.getResource();
					pr.setLocalRequestStatus(sc.getToState());
					pr.setLocalRequestLastCheckTimestamp(Instant.now());
					pr.setLocalRequestStatusRepeat(Long.valueOf(0));
					return Mono.from(patronRequestRepository.update(pr)).flatMap( spr -> auditEventIndication( context, trackingRecord));
				case "BorrowerVirtualItem":
					PatronRequest pr2 = (PatronRequest) sc.getResource();
					pr2.setLocalItemStatus(sc.getToState());
					pr2.setLocalItemLastCheckTimestamp(Instant.now());
					pr2.setLocalItemStatusRepeat(Long.valueOf(0));
					pr2.setLocalRenewalCount(sc.getToRenewalCount());
					return Mono.from(patronRequestRepository.update(pr2)).flatMap( spr -> auditEventIndication( context, trackingRecord));
				case "SupplierItem":
					SupplierRequest sr2 = (SupplierRequest) sc.getResource();
					sr2.setLocalItemStatus(sc.getToState());
					sr2.setLocalItemLastCheckTimestamp(Instant.now());
					sr2.setLocalItemStatusRepeat(Long.valueOf(0));
					sr2.setLocalRenewalCount(sc.getToRenewalCount());
					sr2.setLocalHoldCount(sc.getToHoldCount());
					sr2.setLocalRenewable(sc.getRenewable());
					return Mono.from(supplierRequestRepository.update(sr2)).flatMap(ssr -> auditEventIndication( context, trackingRecord));
				case "PickupRequest":
					PatronRequest pickupRequest = (PatronRequest) sc.getResource();
					pickupRequest.setPickupRequestStatus(sc.getToState());
					pickupRequest.setPickupRequestLastCheckTimestamp(Instant.now());
					pickupRequest.setPickupRequestStatusRepeat(Long.valueOf(0));
					return Mono.from(patronRequestRepository.update(pickupRequest)).flatMap( __ -> auditEventIndication( context, trackingRecord));
				case "PickupItem":
					PatronRequest pickupItem = (PatronRequest) sc.getResource();
					pickupItem.setPickupItemStatus(sc.getToState());
					pickupItem.setPickupItemLastCheckTimestamp(Instant.now());
					pickupItem.setPickupItemStatusRepeat(Long.valueOf(0));
					return Mono.from(patronRequestRepository.update(pickupItem)).flatMap( __ -> auditEventIndication( context, trackingRecord));
			}

			throw Problem.builder()
				.withTitle("State change record for unknown resource type" + sc.getResourceType())
				.with("StateChangeRecord", sc)
				.build();
		}
		else {
			log.error("Unknown tracking record type"+trackingRecord);
			return Mono.empty();
		}
	}

					//case "CONFIRMED" -> handler = "SupplierRequestConfirmed";

	@Transactional
	public Mono<Map<String,Object>> auditEventIndication(Map<String,Object> context,
		TrackingRecord tr) {

			log.debug("Audit event indication");

			final var sc = (StateChange) tr;

			final var msg = "to %s from %s - %s(%s)".formatted(
				sc.getToState(), sc.getFromState(),
				sc.getResourceType(), sc.getResourceId());

			final var auditData = new HashMap<String,Object>();

			auditData.put("patronRequestId", sc.getPatronRequestId());
			auditData.put("resourceType", sc.getResourceType());
			auditData.put("resourceId", sc.getResourceId());
			auditData.put("fromState", sc.getFromState());
			auditData.put("toState", sc.getToState());
			if (sc.getFromRenewalCount() != null) {
				auditData.put("fromRenewalCount", sc.getFromRenewalCount());
			}
			if (sc.getToRenewalCount() != null) {
				auditData.put("toRenewalCount", sc.getToRenewalCount());
			}

			if ( "SupplierItem".equals(sc.getResourceType()) ) {
				auditData.put("fromLocalHoldCount", sc.getFromHoldCount());
				auditData.put("toLocalHoldCount", sc.getToHoldCount());
			}
			
			final var additionalProperties = getValueOrNull(sc, StateChange::getAdditionalProperties);
			if (additionalProperties != null) {
				try {
					auditData.putAll(additionalProperties);
				} catch (Exception e) {
					log.error("Unable to add additional properties to audit data");
				}
			}

			return patronRequestAuditService.addAuditEntry(sc.getPatronRequestId(), msg, auditData)
				.thenReturn(context);
		}
	}

