package org.olf.dcb.request.workflow;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;
import reactor.core.publisher.Mono;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.putAuditData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@Named("ExpeditedCheckoutTransition")
public class ExpeditedCheckoutTransition implements PatronRequestStateTransition {

//	private final TrackingService trackingService;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<PatronRequest.Status> possibleSourceStatus = List.of(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY, PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY);

	public ExpeditedCheckoutTransition( PatronRequestRepository patronRequestRepository, 	BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider, PatronRequestAuditService patronRequestAuditService)
																			 {
//		this.trackingService = trackingService;
		this.patronRequestRepository = patronRequestRepository;
																				 this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
																				 this.patronRequestAuditService = patronRequestAuditService;

	}

	private String[] extractPatronBarcodes(String inputstr) {
		String[] result = null;
		if (inputstr != null) {
			if (inputstr.startsWith("[")) {
				result = inputstr.substring(1, inputstr.length() - 1).split(", ");
			} else {
				return inputstr.split(", ");
			}
		}
		return result;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final boolean isStatusApplicable = possibleSourceStatus.contains(ctx.getPatronRequest().getStatus());
		log.debug("Status applicable? {}", isStatusApplicable);
		log.debug("Expedited checkout? {}", ctx.getPatronRequest().getIsExpeditedCheckout());

		return isStatusApplicable && ctx.getPatronRequest().getIsExpeditedCheckout();
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("Execute action: ExpeditedCheckoutTransition...");

		log.debug("Status: {}",ctx.getPatronRequest().getStatus());
		log.debug("We would do the checkout here");
		final String[] patron_barcodes = extractPatronBarcodes(ctx.getPatronVirtualIdentity().getLocalBarcode());
		// previous code was causing a loop which kills everything. one assumes there is a better way of forcing it to progress to the status
		// can we get to REQUEST_PLACED_AT_BORROWING_AGENCY in a legit, but faster fashion

		ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED);



		// Add workflow message
		ctx.getWorkflowMessages().add("Expedited checkout completed - item status changed to LOANED");

		// Create audit entry with workflow messages
		HashMap<String, Object> auditData = new HashMap<>();
		auditData.put("workflowMessages", ctx.getWorkflowMessages());
		auditData.put("expeditedCheckout", true);
		auditData.put("sourceStatus", ctx.getPatronRequestStateOnEntry());

		auditData.put("virtual-patron-barcode", Arrays.toString(patron_barcodes));


		// Add audit, update patron request once done.
		return patronRequestAuditService.addAuditEntry(
				ctx.getPatronRequest(),
				"ExpeditedCheckoutTransition has completed",
				auditData)
			.then(updatePatronRequest(ctx));
//		return patronRequestWorkflowServiceProvider.get().progressUsing(ctx)
//			.doOnError(error -> log.error("Problem attempting to progress request",error))
//			.doOnSuccess(context -> log.info("Context status {}",context.getPatronRequest().getStatus()));
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.LOANED);
	}

	@Override
	public List<PatronRequest.Status> getPossibleSourceStatus() {
		// ideally, we would want to only skip from REQUEST_PLACED_AT_BORROWING_AGENCY onwards
		// but that may not be possible
//		return List.of(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY, PatronRequest.Status.CONFIRMED, PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY);
		return possibleSourceStatus;
	}

	@Override
	public boolean attemptAutomatically() {
		// Should be true ONLY if this is automatically if the condition is met
		return true;
	}

	@Override
	public String getName() {
		return "ExpeditedCheckoutTransition";
	}

	private Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext requestWorkflowContext) {
		return Mono.from(patronRequestRepository.saveOrUpdate(requestWorkflowContext.getPatronRequest()))
			.thenReturn(requestWorkflowContext);
	}
}
