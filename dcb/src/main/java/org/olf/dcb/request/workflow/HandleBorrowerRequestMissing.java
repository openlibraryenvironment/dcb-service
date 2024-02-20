package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;

import java.util.Map;
import java.util.Objects;

import org.olf.dcb.core.model.PatronRequest;
import  org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit.PatronRequestAuditBuilder;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Singleton
@Named("BorrowerRequestMissing")
public class HandleBorrowerRequestMissing implements WorkflowAction {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public HandleBorrowerRequestMissing(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService patronRequestAuditService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.patronRequestAuditService = patronRequestAuditService;
	}
	
	private static void transitionToState ( final PatronRequest pr, final PatronRequestAuditBuilder audit, Status toState, String reason ) {
		
		audit
			.patronRequest(pr)
			.fromStatus(pr.getStatus())
			.toStatus(toState)
			.briefDescription(reason);
		
		// Ensure after we've extracted "toState"
		pr.setStatus(toState);
	}
	
	private static void noop ( final PatronRequest pr, final PatronRequestAuditBuilder audit, String reason ) {
		audit
			.patronRequest(pr)
			.fromStatus(pr.getStatus())
			.toStatus(pr.getStatus())
			.briefDescription(reason);
	}
	
	private static void handleStateTransitions ( Tuple2<PatronRequest,SupplierRequest> tuple, final PatronRequestAuditBuilder audit ) {
		handleStateTransitions(tuple.getT1(), tuple.getT2(), audit);
	}
	
	private static void handleStateTransitions ( final PatronRequest pr, final SupplierRequest sr, final PatronRequestAuditBuilder audit ) {


		// First we have to make sure that we record the downstream value of the hold request or we will keep calling this method
		pr.setLocalRequestStatus("MISSING");

		
		// Patron cancels request, sierra deletes request to represent the cancellation
		// IF the item isn't already on the holdshelf then we can cancel
		// if (!Objects.equals(pr.getLocalItemStatus(), ITEM_ON_HOLDSHELF)) {
		// Rewriting this condition - it's not good. For now - IF the request is in a state of PLACED_AT_BORROWING_AGENCY then that means it's not
		// yet been filled by the supplying library - and that means that cancellation at the supplier is meaningful. More specifically - ANY of the 
		// states before PICKUP_TRANSIT are meaningful states for CANCEL.
		if ( ( pr.getStatus() == REQUEST_PLACED_AT_BORROWING_AGENCY ) ||
				( pr.getStatus() == REQUEST_PLACED_AT_SUPPLYING_AGENCY ) ||
				( pr.getStatus() == PATRON_VERIFIED ) ||
				( pr.getStatus() == SUBMITTED_TO_DCB ) ||
				( pr.getStatus() == RESOLVED ) ) {
			
			transitionToState(pr, audit, CANCELLED, "Missing borrower request when local request status was Item on hold shelf");
			log.debug("setting DCB internal status to CANCELLED {}",pr);
		}
		// Patron cancels request, polaris sets hold status to Cancelled
		else if (Objects.equals(sr.getLocalStatus(), "CANCELLED")) {
			if ( pr.getStatus() == FINALISED ) {
				noop(pr, audit, "The request in the supplier system was CANCELLED or deleted after the DCB request was FINALISED" );
			}
			else {
				transitionToState(pr, audit, CANCELLED, "Borrower system request MISSING, Supplier request CANCELLED");
				log.info("Borrower request MISSING - setting DCB internal status to CANCELLED {}",pr);
			}
		}
	// item has been returned home from borrowing library
		// This isn't correct - the place to do this is when the supplying library item is available and the state
		// was in transit back to the lending system
		// else if (Objects.equals(sr.getLocalItemStatus(), ITEM_AVAILABLE)) {
		// 	patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), COMPLETED, 
		// 		Optional.of("Missing borrower request when local local status was AVAILABLE"));
		// 	log.info("setting DCB internal status to COMPLETED because item status AVAILABLE {}",pr);
     //  // ToDo - Consider if this should be BORROWER-COMPLETED to indicate that the return
      // part of the transaction is still to be completed
		// 	return pr.setStatus(COMPLETED);
		// }

		else {
			noop(pr, audit, "borrower request missing, no special action");
		}
		log.debug("No matched condition for changing DCB internal status {}",pr);
	}

	@Transactional
	public Mono<Map<String,Object>> execute(Map<String,Object> context) {
		
		// This method is called when we detect that the patron request in the borrowing system is no longer present
		return Mono.justOrEmpty((StateChange) context.get("StateChange"))
			.doOnSuccess( stateChange -> log.debug("HandleBorrowerRequestMissing {}", stateChange))
			.map( stateChange -> {
				PatronRequest patronRequest = (PatronRequest)stateChange.getResource();
				return patronRequest.setLocalItemStatus(stateChange.getToState());
			})
			
			// Combine with the supplier request
			.zipWhen( patronRequest -> Mono.from(supplierRequestRepository.findByPatronRequest( patronRequest )) )
			
			// Use the patron request service to bind our mutating handler.
			// Any errors should be 
			.transform( patronRequestAuditService.withAuditMessageNoPropagateErrors( HandleBorrowerRequestMissing::handleStateTransitions ))
			
			// We now only need the PatronRequest from here.
			.map( Tuple2::getT1 )
			
			.doOnNext( patronRequest -> log.debug("setLocalRequestStatus to {}", patronRequest.getLocalRequestStatus()) )
			.flatMap( request -> Mono.from(patronRequestRepository.saveOrUpdate(request)) )
			
			.doOnSuccess( spr -> {
				if (spr == null) {
					log.warn("Unable to locate patron request to mark as missing");
					return;
				}
				
				log.debug("Saved {} - check if we need to do any cleanup", spr);
			})
	
			// Call the workflow service to see if we can advance the state of the request at all - in particular
			// to clean up any completed requests. ToDo: Consider if this should be moved higher up the chain to see
			// if there are progressions after any tracking notification
			.flatMap( spr -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(spr)) )
			
			.thenReturn(context);
	}
}
