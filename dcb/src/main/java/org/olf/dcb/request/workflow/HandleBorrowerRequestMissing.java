package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import io.micronaut.context.BeanProvider;

import javax.transaction.Transactional;
import java.util.Map;
import java.util.Objects;

import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_ON_HOLDSHELF;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;

@Singleton
@Named("BorrowerRequestMissing")
public class HandleBorrowerRequestMissing implements WorkflowAction {

	private static final Logger log = LoggerFactory.getLogger(HandleBorrowerRequestMissing.class);

	private PatronRequestRepository patronRequestRepository;
	private SupplierRequestRepository supplierRequestRepository;
        // Provider to prevent circular reference exception by allowing lazy access to this singleton.
        private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public HandleBorrowerRequestMissing(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
                BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider)
	{
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
                this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
	}

	@Transactional
	public Mono<Map<String,Object>> execute(Map<String,Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerRequestMissing {}",sc);

		PatronRequest pr = (PatronRequest) sc.getResource();

		if (pr != null) {
			return Mono.from(supplierRequestRepository.findByPatronRequest(pr))
				.map(sr -> {
					// Patron cancels request, sierra deletes request
					if (!Objects.equals(pr.getLocalItemStatus(), ITEM_ON_HOLDSHELF)) {
						log.debug("setting DCB internal status to CANCELLED {}",pr);
						return pr.setStatus(CANCELLED);
					}
					// item has been returned home from borrowing library
					if (Objects.equals(sr.getLocalItemStatus(), ITEM_AVAILABLE)) {
						log.debug("setting DCB internal status to COMPLETED because item status AVAILABLE {}",pr);
						return pr.setStatus(COMPLETED);
					}
					// item has not been despatched from lending library
					if (Objects.equals(sr.getLocalStatus(), "PLACED")) {
						log.debug("setting DCB internal status to COMPLETED because sr local status PLACED {}",pr);
						return pr.setStatus(COMPLETED);
					}
					log.debug("No matched condition for changing DCB internal status {}",pr);
					return pr;
				})
				.map(patronRequest -> patronRequest.setLocalRequestStatus("MISSING"))
				.doOnNext(patronRequest -> log.debug("setLocalRequestStatus to MISSING {}", patronRequest))
				.flatMap(request -> Mono.from(patronRequestRepository.saveOrUpdate(request)))
				.doOnNext(spr -> log.debug("Saved {} - check if we need to do any cleanup", spr))
                                // Call the workflow service to see if we can advance the state of the request at all - in particular
                                // to clean up any completed requests
                                .flatMap( spr -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(spr)) )
				.thenReturn(context);
		}
		else {
			log.warn("Unable to locate patron request to mark as missing");
			return Mono.just(context);
		}
	}
}
