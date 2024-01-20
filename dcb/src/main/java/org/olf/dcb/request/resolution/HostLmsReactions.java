package org.olf.dcb.request.resolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.olf.dcb.tracking.model.StateChange;
import io.micronaut.context.annotation.Context;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.olf.dcb.request.workflow.WorkflowAction;
import io.micronaut.context.ApplicationContext;
import java.util.Map;
import java.util.HashMap;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.transaction.Transactional;
import java.util.List;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 */
@Singleton
public class HostLmsReactions {

	private static final Logger log = LoggerFactory.getLogger(HostLmsReactions.class);
	private final ApplicationContext appContext;
	private final R2dbcOperations r2dbcOperations;
	private final PatronRequestAuditService patronRequestAuditService;

	// Ensure that we have loaded and initialised all workflow actions
	private final List<WorkflowAction> allWorkflowActions;


	public HostLmsReactions(ApplicationContext appContext,
		R2dbcOperations r2dbcOperations,
		List<WorkflowAction> allWorkflowActions,
		PatronRequestAuditService patronRequestAuditService) {
		this.appContext = appContext;
		this.r2dbcOperations = r2dbcOperations;
		this.allWorkflowActions = allWorkflowActions;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@jakarta.annotation.PostConstruct
	private void init() {
		log.info("HostLmsReactions::init");
		for ( WorkflowAction w: allWorkflowActions ) {
			log.info("Workflow action: {}",w);
		}
	}

	@Transactional
	public Mono<Map<String,Object>> onTrackingEvent(TrackingRecord trackingRecord) {
		log.debug("onTrackingEvent {}",trackingRecord);
		String handler = null;

		// ToDo: This method should absolutely write a patron_request_audit record noting that the change was detected.
		Map<String,Object> context = new HashMap();

		if ( trackingRecord.getTrackigRecordType().equals(StateChange.STATE_CHANGE_RECORD) ) {
			StateChange sc = (StateChange) trackingRecord;
			context.put("StateChange",sc);

			// This will be replaced by a table in the near future
			if ( sc.getResourceType().equals("SupplierRequest") ) {
				switch (sc.getToState()) {
					case "TRANSIT" -> handler = "SupplierRequestInTransit";
					case "MISSING" -> handler = "SupplierRequestMissing";
					case "PLACED" -> handler = "SupplierRequestPlaced";
					case "CANCELLED" -> handler = "SupplierRequestCancelled";
					default -> handler = "SupplierRequestUnhandledState";
				}
			}
			else if ( sc.getResourceType().equals("PatronRequest") ) {
				// Patron cancels request at borrowing library then status will be MISSING or CANCELLED
				if (sc.getToState().equals("MISSING") || sc.getToState().equals("CANCELLED")) {
					handler = "BorrowerRequestMissing";
				} else {
					log.error("Unhandled PatronRequest ToState:{}", sc.getToState());
				}
			}
			else if ( sc.getResourceType().equals("BorrowerVirtualItem") ) {
				// See org.olf.dcb.core.interaction.HostLmsItem :
				// MISSING AVAILABLE TRANSIT OFFSITE HOLDSHELF RECEIVED LIBRARY_USE_ONLY RETURNED LOANED
				if ( sc.getFromState() != null && sc.getFromState().equals("LOANED") && 
                                     sc.getToState() != null && sc.getToState().equals("TRANSIT") ) {
					// If we had a prior state, then this is return transit
					handler="BorrowerRequestReturnTransit";
				}
				else if ( sc.getToState().equals("TRANSIT") ) {
					handler="BorrowerRequestItemInTransit";
				}
				else if ( sc.getToState().equals("AVAILABLE") ) {
					handler="BorrowerRequestItemAvailable";
				}
				else if ( sc.getToState().equals("LOANED") ) {
					handler="BorrowerRequestLoaned";
				}
				else if ( sc.getToState().equals("HOLDSHELF") ) {
					handler="BorrowerRequestItemOnHoldShelf";
				}
				else if ( sc.getToState().equals("RECEIVED") ) {
					handler="BorrowerRequestItemReceived";
				}
				else if ( sc.getToState().equals("MISSING") ) {
					handler="BorrowerRequestItemMissing";
				}
				else {
					handler="BorrowerItemUnhandledState";
				}
			}
			else if ( sc.getResourceType().equals("SupplierItem") ) {
				if (sc.getToState().equals("AVAILABLE")) {
					handler = "SupplierRequestItemAvailable";
				} else {
					// A noop handler that just updates the tracked state so we know what it has changed to.
					handler = "SupplierRequestItemStateChange";
				}
			}
			else {
				log.error("Unhandled resource type for status change record {}",sc);
			}
		}
		else {
			log.warn("Unhandled tracking record type {}",trackingRecord.getTrackigRecordType());
		}

		// https://stackoverflow.com/questions/74183112/how-to-select-the-correct-transactionmanager-when-using-r2dbc-together-with-flyw
		if ( handler != null ) {
		  log.debug("onTrackingEvent Detected handler: {}",handler);
			log.debug("Attempt to resolve bean");
			WorkflowAction action = appContext.getBean(WorkflowAction.class, Qualifiers.byName(handler));
			if ( action != null ) {
				log.debug("Invoke {}",action.getClass().getName());
				return auditEventIndication(context, trackingRecord)
					.flatMap(ctx -> action.execute(ctx))
					.onErrorResume( error -> Mono.defer(() -> {
						log.error("Problem in reaction - we should write an audit here",error);
						return Mono.empty();
					}))
					.flatMap ( ctx -> {
					  log.debug("Action completed - we should write an audit here:"+ctx);
						return Mono.just(ctx);
					})
					.thenReturn(context);
			}
			else {
				throw new RuntimeException("Missing qualified WorkflowAction for handler "+handler);
			}
		}
		else {
			log.warn("No handler for state change {}",trackingRecord);
		}

		log.debug("onTrackingEvent {} complete",trackingRecord);
		return Mono.just(context);
	}

	private Mono<Map<String,Object>> auditEventIndication(Map<String,Object> context, TrackingRecord tr) {
		log.debug("Audit event indication");
		StateChange sc = (StateChange) tr;
		String msg = "Downstream change to "+sc.getResourceType()+"("+sc.getResourceId()+") to "+sc.getToState()+" from "+sc.getFromState();
		Map<String,Object> auditData = new HashMap();
		auditData.put("patronRequestId",sc.getPatronRequestId());
		auditData.put("resourceType",sc.getResourceType());
		auditData.put("resourceId",sc.getResourceId());
		auditData.put("fromState",sc.getFromState());
		auditData.put("toState",sc.getToState());

		return patronRequestAuditService.addAuditEntry(sc.getPatronRequestId(), msg, auditData)
			.thenReturn(context);
	}
}

