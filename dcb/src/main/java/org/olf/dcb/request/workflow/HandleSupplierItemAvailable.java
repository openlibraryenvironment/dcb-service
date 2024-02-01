package org.olf.dcb.request.workflow;

import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.tracking.model.StateChange;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

@Slf4j
@Singleton
@Named("SupplierRequestItemAvailable")
public class HandleSupplierItemAvailable implements WorkflowAction {
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService auditService;
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

	public HandleSupplierItemAvailable(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		PatronRequestAuditService auditService, RequestWorkflowContextHelper requestWorkflowContextHelper,
		HostLmsService hostLmsService) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.auditService = auditService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.hostLmsService = hostLmsService;
	}

	@Transactional
	public Mono<Map<String,Object>> execute(Map<String,Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleSupplierItemAvailable {}",sc);
		SupplierRequest sr = (SupplierRequest) sc.getResource();
		return Mono.from(patronRequestRepository.findById(sr.getPatronRequest().getId()))
			.flatMap(pr -> {
				if (sr != null && pr != null) {
					// If we've been through the lifecycle of a request and the item is available again at the
					// supplying library then it's returned. But if this is a new request, the item is likely
					// available because it's on the shelf. If we have detected some state other than AVAILABLE
					// that means that the process is now in flow.
					return handleSupplierLocalItemStatus(sr, pr)
						.doOnSuccess(ok -> log.debug("Set local status to AVAILABLE and save {}", sr))
						.flatMap(ok -> Mono.from(supplierRequestRepository.saveOrUpdate(sr)))
						.doOnNext(ssr -> log.debug("Saved {}", ssr))
						.flatMap(ssr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
						.doOnNext(spr -> log.debug("Saved {}", spr))
						// See if we can progress the patron request at all
						.flatMap(spr -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(spr)))
						.thenReturn(context);
				} else {
					log.warn("Unable to locate supplier request to mark item as available");
					return Mono.just(context);
				}
			});
	}

	private Mono<String> handleSupplierLocalItemStatus(SupplierRequest sr, PatronRequest pr) {
		if (sr.getLocalItemStatus() == null) {
			// Our first time seeing this item set it's state to AVAILABLE
			auditService.addAuditEntry(pr, "Supplier Item Available - Infers this item is available for a new request").subscribe();

			log.debug("Initialising supplying library item status");
			sr.setLocalItemStatus("AVAILABLE");

			return Mono.just("OK");
		}
		else {
			// An item becoming available means the request process has 'completed'

			// DCB-851 update borrowing lib
			return updateHostLmsClients(pr)
				// when successful update dcb
				.doOnSuccess(ok -> {
					log.debug("Finalising supplier request - item is available at lender again");
					auditService.addAuditEntry(pr,
						"Supplier Item Available - Infers item back on the shelf after loan. Completing request")
						.subscribe();

					sr.setLocalItemStatus("AVAILABLE");
					pr.setStatus(PatronRequest.Status.COMPLETED);
				});
		}
	}

	private Mono<String> updateHostLmsClients(PatronRequest pr) {
		return Mono.zip( Mono.just(pr), requestWorkflowContextHelper.fromPatronRequest(pr) )
			.flatMap(function(this::updateBorrowerThatItemHasBeenReceivedBack));
	}

	private Mono<String> updateBorrowerThatItemHasBeenReceivedBack(
		PatronRequest patronRequest, RequestWorkflowContext requestWorkflowContext) {

		final var localId = patronRequest.getLocalRequestId();
		final var localItemId = patronRequest.getLocalItemId();

		return hostLmsService.getClientFor(requestWorkflowContext.getPatronSystemCode())
			.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(
				localItemId, HostLmsClient.CanonicalItemState.COMPLETED, localId));
	}
}
