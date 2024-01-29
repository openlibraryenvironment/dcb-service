package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.OFFSITE;

import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("BorrowerRequestItemAvailable")
public class HandleBorrowerItemAvailable implements WorkflowAction {
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;

	public HandleBorrowerItemAvailable(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerItemAvailable {}", sc);
		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setLocalItemStatus(sc.getToState());

			return requestWorkflowContextHelper.fromPatronRequest(pr)
				// For now we have decided we don't want to set ItemStatus to OFF CAMPUS preferring
				// to wait for checkout to patron
				// .flatMap( this::updateSupplyingSystems )
				.flatMap(rwc -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
				.doOnNext(spr -> log.debug("Saved {}", spr))
				.doOnError(error -> log.error("Error occurred in handle item available: ", error))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate patron request to mark as available");
			return Mono.just(context);
		}
	}


	public Mono<RequestWorkflowContext> updateSupplyingSystems(RequestWorkflowContext rwc) {
		PatronRequest pr = rwc.getPatronRequest();
		// If we are in RET-STD workflow, an item becomming available at the borrowing agency is an indication that it is
		// Checked in, and we should update the status of the item at the lending institution.
		if (pr.getActiveWorkflow() != null) {
			if (pr.getActiveWorkflow().equals("RET-STD")) {
				// Standard workflow - remote borrower, patron picking up from a library in the home system
				log.info(
					"RET-STD:: We should tell the lender system that the item has arrived");
				return setHomeItemOffCampus(rwc);
			} else {
				log.warn("Unhandled workflow {}", pr.getActiveWorkflow());
			}
		} else {
			log.warn("Request has no active workflow, just store state");
		}
		return Mono.just(rwc);
	}

	public Mono<RequestWorkflowContext> setHomeItemOffCampus(RequestWorkflowContext rwc) {
		if ((rwc.getSupplierRequest() != null) &&
			(rwc.getSupplierRequest().getLocalItemId() != null) &&
			(rwc.getLenderSystemCode() != null)) {
			log.debug("Update supplying system item: {}",
				rwc.getSupplierRequest().getLocalItemId());

			final var supplierRequest = rwc.getSupplierRequest();

			return hostLmsService.getClientFor(rwc.getLenderSystemCode())
				.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(
					supplierRequest.getLocalItemId(), OFFSITE, supplierRequest.getLocalId()))
				.thenReturn(rwc);
		} else {
			log.error("Missing data attempting to set home item off campus {}", rwc);
			return Mono.just(rwc);
		}
	}
}
