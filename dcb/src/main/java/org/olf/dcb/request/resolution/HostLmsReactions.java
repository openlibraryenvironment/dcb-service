package org.olf.dcb.request.resolution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.workflow.WorkflowAction;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.tracking.model.TrackingRecord;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 */
@Slf4j
@Singleton
public class HostLmsReactions {
	private final ApplicationContext appContext;
	private final PatronRequestAuditService patronRequestAuditService;

	// Ensure that we have loaded and initialised all workflow actions
	private final List<WorkflowAction> allWorkflowActions;

	public HostLmsReactions(ApplicationContext appContext,
		PatronRequestAuditService patronRequestAuditService,
		List<WorkflowAction> allWorkflowActions) {

		this.appContext = appContext;
		this.allWorkflowActions = allWorkflowActions;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@jakarta.annotation.PostConstruct
	private void init() {
		log.info("HostLmsReactions::init");
		for (WorkflowAction w : allWorkflowActions) {
			log.info("Workflow action: {}", w);
		}
	}

	@Transactional
	public Mono<Map<String,Object>> onTrackingEvent(TrackingRecord trackingRecord) {
		log.debug("onTrackingEvent {}", trackingRecord);
		String handler = null;

		// ToDo: This method should absolutely write a patron_request_audit record noting that the change was detected.
		Map<String,Object> context = new HashMap<>();

		if (trackingRecord.getTrackigRecordType().equals(StateChange.STATE_CHANGE_RECORD)) {
			StateChange sc = (StateChange) trackingRecord;
			context.put("StateChange", sc);

			// This will be replaced by a table in the near future
			if (sc.getResourceType().equals("SupplierRequest")) {
				switch (sc.getToState()) {
					case "TRANSIT" -> handler = "SupplierRequestInTransit";
					case "MISSING" -> handler = "SupplierRequestMissing";
					case "PLACED" -> handler = "SupplierRequestPlaced";
					case "CANCELLED" -> handler = "SupplierRequestCancelled";
					default -> handler = "SupplierRequestUnhandledState";
				}
			}
			else if (sc.getResourceType().equals("PatronRequest")) {
				// Patron cancels request at borrowing library then status will be MISSING or CANCELLED
				if (sc.getToState().equals("MISSING") || sc.getToState().equals("CANCELLED")) {
					handler = "BorrowerRequestMissing";
				} else {
					log.error("Unhandled PatronRequest ToState:{}", sc.getToState());
				}
			}
			else if (sc.getResourceType().equals("BorrowerVirtualItem")) {
				// See org.olf.dcb.core.interaction.HostLmsItem :
				// MISSING AVAILABLE TRANSIT OFFSITE HOLDSHELF RECEIVED LIBRARY_USE_ONLY RETURNED LOANED
				if (sc.getFromState() != null && sc.getFromState().equals("LOANED") &&
					sc.getToState() != null && sc.getToState().equals("TRANSIT")) {

					// If we had a prior state, then this is return transit
					handler="BorrowerRequestReturnTransit";
				}
				else if (sc.getToState().equals("TRANSIT")) {
					handler="BorrowerRequestItemInTransit";
				}
				else if (sc.getToState().equals("AVAILABLE")) {
					handler="BorrowerRequestItemAvailable";
				}
				else if (sc.getToState().equals("LOANED")) {
					handler="BorrowerRequestLoaned";
				}
				else if (sc.getToState().equals("HOLDSHELF")) {
					handler="BorrowerRequestItemOnHoldShelf";
				}
				else if (sc.getToState().equals("RECEIVED")) {
					handler="BorrowerRequestItemReceived";
				}
				else if (sc.getToState().equals("MISSING")) {
					handler="BorrowerRequestItemMissing";
				}
				else {
					handler="BorrowerItemUnhandledState";
				}
			}
			else if (sc.getResourceType().equals("SupplierItem")) {
				if (sc.getToState().equals("AVAILABLE")) {
					handler = "SupplierRequestItemAvailable";
				} else {
					// A noop handler that just updates the tracked state so we know what it has changed to.
					handler = "SupplierRequestItemStateChange";
				}
			}
			else {
				log.error("Unhandled resource type for status change record {}", sc);
			}
		}
		else {
			log.warn("Unhandled tracking record type {}", trackingRecord.getTrackigRecordType());
		}

		// https://stackoverflow.com/questions/74183112/how-to-select-the-correct-transactionmanager-when-using-r2dbc-together-with-flyw
		if (handler != null) {
		  log.debug("onTrackingEvent Detected handler: {}", handler);
			log.debug("Attempt to resolve bean");

			final WorkflowAction action = appContext.getBean(WorkflowAction.class, Qualifiers.byName(handler));
			if (action != null) {
				log.debug("Invoke {}",action.getClass().getName());
				return auditEventIndication(context, trackingRecord, handler)
					.flatMap(action::execute)
					.onErrorResume(error -> Mono.defer(() -> {
						log.error("Problem in reaction handler="+action.getClass().getName()+" - we should write an audit here", error);
						return Mono.empty();
					}))
					.flatMap(ctx -> {
					  log.debug("Action completed - we should write an audit here:" + ctx);
						return Mono.just(ctx);
					})
					.thenReturn(context);
			}
			else {
				throw new RuntimeException("Missing qualified WorkflowAction for handler " + handler);
			}
		}
		else {
			log.warn("No handler for state change {}", trackingRecord);
		}

		log.debug("onTrackingEvent {} complete", trackingRecord);
		return Mono.just(context);
	}

	@Transactional
	public Mono<Map<String,Object>> auditEventIndication(Map<String,Object> context, TrackingRecord tr, String handler) {
		log.debug("Audit event indication");
		StateChange sc = (StateChange) tr;
		String msg = "Downstream change to " + sc.getResourceType() + "(" + sc.getResourceId() + ") to " + sc.getToState() + " from " + sc.getFromState() + " triggers "+handler;
		Map<String,Object> auditData = new HashMap<>();
		auditData.put("patronRequestId",sc.getPatronRequestId());
		auditData.put("resourceType",sc.getResourceType());
		auditData.put("resourceId",sc.getResourceId());
		auditData.put("fromState",sc.getFromState());
		auditData.put("toState",sc.getToState());

		return patronRequestAuditService.addAuditEntry(sc.getPatronRequestId(), msg, auditData)
			.thenReturn(context);
	}
}

