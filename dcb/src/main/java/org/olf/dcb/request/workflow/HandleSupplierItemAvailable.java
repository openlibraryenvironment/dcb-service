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
  private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private HostLmsService hostLmsService;

	public HandleSupplierItemAvailable(PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		PatronRequestAuditService auditService,
		RequestWorkflowContextHelper requestWorkflowContextHelper,
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

    return requestWorkflowContextHelper.fromSupplierRequest(sr)
			.flatMap ( ctx -> handleSupplierLocalItemStatusAvailable(ctx) )
			.thenReturn(context);
	}

	private Mono<RequestWorkflowContext> handleSupplierLocalItemStatusAvailable(RequestWorkflowContext ctx) {

		SupplierRequest sr = ctx.getSupplierRequest();
		PatronRequest pr = ctx.getPatronRequest();

		// This should not happen...
		if ( ( sr == null ) || ( pr == null ) )
			return Mono.error(new RuntimeException("Unable to locate supplier or patron request"));

		// If this is a new request and we are detecting that the supplier item is available then it is available for the
		// borrower - update and continue
		if (sr.getLocalItemStatus() == null) {
			// Our first time seeing this item set it's state to AVAILABLE
			log.debug("Initialising supplying library item status");
			sr.setLocalItemStatus("AVAILABLE");
			return Mono.from(supplierRequestRepository.saveOrUpdate(sr))
				.flatMap( ssr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)) )
				.flatMap( spr -> auditService.addAuditEntry(spr, "Supplier Item Available - Infers selected item is available for request") )
				.flatMap( aud -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(pr)))
				.thenReturn(ctx);
		}
		else {
			// An item becoming available means the request process has 'completed'
			sr.setLocalItemStatus("AVAILABLE");
			pr.setStatus(PatronRequest.Status.COMPLETED);

			// DCB-851 update borrowing lib
			return updateBorrowerThatItemHasBeenReceivedBack(pr)
				.flatMap( ok -> Mono.from(supplierRequestRepository.saveOrUpdate(sr) ) )
				.flatMap( ssr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)) )
				.flatMap( spr -> auditService.addAuditEntry(spr, "Supplier Item Available - Infers item back on the shelf after loan. Completing request") )
				.flatMap( aud -> Mono.from(patronRequestWorkflowServiceProvider.get().progressAll(pr)))
				.thenReturn(ctx);
		}
	}

	private Mono<String> updateBorrowerThatItemHasBeenReceivedBack(PatronRequest patronRequest) {

		final var localId = patronRequest.getLocalRequestId();
		final var localItemId = patronRequest.getLocalItemId();

		return hostLmsService.getClientFor(patronRequest.getPatronHostlmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.updateItemStatus(localItemId, HostLmsClient.CanonicalItemState.COMPLETED, localId));
	}
}
