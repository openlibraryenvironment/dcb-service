package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_ON_HOLDSHELF;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
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

	@Transactional
	public Mono<Map<String,Object>> execute(Map<String,Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerRequestMissing {}",sc);

		// This method is called when we detect that the patron request in the borrowing system is no longer present
		PatronRequest pr = (PatronRequest) sc.getResource();

		if (pr != null) {
			pr.setLocalItemStatus(sc.getToState());
			return Mono.from(supplierRequestRepository.findByPatronRequest(pr))
				.map(sr -> {
					// Patron cancels request, sierra deletes request to represent the cancellation
					// IF the item isn't already on the holdshelf then we can cancel
					if (!Objects.equals(pr.getLocalItemStatus(), ITEM_ON_HOLDSHELF)) {
						patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), CANCELLED, 
							Optional.of("Missing borrower request when local request status was Item on hold shelf"));
						log.debug("setting DCB internal status to CANCELLED {}",pr);
						return pr.setStatus(CANCELLED);
					}
					// Patron cancels request, polaris sets hold status to Cancelled
					else if (Objects.equals(sr.getLocalStatus(), "CANCELLED")) {
						if ( pr.getStatus() == FINALISED ) {
							patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
								Optional.of("The request in the supplier system was CANCELLED or deleted after the DCB request was FINALISED"));
							// No change
							return pr;
						}
						else {
							patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), CANCELLED,
								Optional.of("Borrower system request MISSING, Supplier request CANCELLED"));
							log.info("Borrower request MISSING - setting DCB internal status to CANCELLED {}",pr);
							return pr.setStatus(CANCELLED);
						}
					}
					// item has been returned home from borrowing library
					else if (Objects.equals(sr.getLocalItemStatus(), ITEM_AVAILABLE)) {
						patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), COMPLETED, 
							Optional.of("Missing borrower request when local local status was AVAILABLE"));
						log.info("setting DCB internal status to COMPLETED because item status AVAILABLE {}",pr);
            // ToDo - Consider if this should be BORROWER-COMPLETED to indicate that the return
            // part of the transaction is still to be completed
						return pr.setStatus(COMPLETED);
					}
					// item has not been despatched from lending library
					else if (Objects.equals(sr.getLocalStatus(), "PLACED")) {
						patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), CANCELLED, 
							Optional.of("Missing borrower request when local request status was PLACED"));
						log.debug("setting DCB internal status to COMPLETED because sr local status PLACED {}",pr);
						return pr.setStatus(CANCELLED);
					}
					else {
						patronRequestAuditService.addAuditEntry(pr, pr.getStatus(), pr.getStatus(), 
							Optional.of("borrower request missing, no special action"));
					}
					log.debug("No matched condition for changing DCB internal status {}",pr);
					return pr;
				})
				.map(patronRequest -> {
					if (Objects.equals(patronRequest.getLocalRequestStatus(), "CANCELLED")) 
						return patronRequest;

					return patronRequest.setLocalRequestStatus("MISSING");
				})
				.doOnNext(patronRequest -> log.debug("setLocalRequestStatus to {}", patronRequest.getLocalRequestStatus()))
				.flatMap(request -> Mono.from(patronRequestRepository.saveOrUpdate(request)))
				.doOnNext(spr -> log.debug("Saved {} - check if we need to do any cleanup", spr))

				// Call the workflow service to see if we can advance the state of the request at all - in particular
				// to clean up any completed requests. ToDo: Consider if this should be moved higher up the chain to see
				// if there are progressions after any tracking notification
				.flatMap(spr -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(spr)))

				.thenReturn(context);
		}
		else {
			log.warn("Unable to locate patron request to mark as missing");
			return Mono.just(context);
		}
	}
}
