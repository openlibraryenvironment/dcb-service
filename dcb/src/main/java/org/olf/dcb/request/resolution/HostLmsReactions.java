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
import javax.transaction.Transactional;
import java.util.List;

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

	// Ensure that we have loaded and initialised all workflow actions
	private final List<WorkflowAction> allWorkflowActions;


	public HostLmsReactions(ApplicationContext appContext,
		R2dbcOperations r2dbcOperations,
		List<WorkflowAction> allWorkflowActions) {
		this.appContext = appContext;
		this.r2dbcOperations = r2dbcOperations;
		this.allWorkflowActions = allWorkflowActions;
	}

	@javax.annotation.PostConstruct
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
					default -> log.error("Unhandled SupplierRequest ToState:{}", sc.getToState());
				}
			}
			else if ( sc.getResourceType().equals("PatronRequest") ) {
				// Patron cancels request at borrowing library then status will be MISSING
				if (sc.getToState().equals("MISSING")) {
					handler = "BorrowerRequestMissing";
				} else {
					log.error("Unhandled PatronRequest ToState:{}", sc.getToState());
				}
			}
			else if ( sc.getResourceType().equals("BorrowerVirtualItem") ) {
				if ( sc.getFromState() != null && sc.getFromState().equals("LOANED") && 
                                     sc.getToState() != null && sc.getToState().equals("TRANSIT") ) {
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
				else if ( sc.getToState().equals("ON_HOLD_SHELF") ) {
					handler="BorrowerRequestItemOnHoldShelf";
				}
				else {
					log.error("Unhandled BorrowerVirtualItem ToState:{}",sc.getToState());
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
			log.debug("Invoke action {}",handler);
			WorkflowAction action = appContext.getBean(WorkflowAction.class, Qualifiers.byName(handler));
			if ( action != null ) {
				log.debug("Invoke {}",action.getClass().getName());
				return action.execute(context)
					.doOnNext(ctx -> log.debug("Action completed:"+ctx))
					.thenReturn(context);
			}
			else {
				throw new RuntimeException("Missing qualified WorkflowAction for handler "+handler);
			}
		}

		return Mono.just(context);
	}

}

