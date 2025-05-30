package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@Named("ExpeditedCheckoutTransition")
public class ExpeditedCheckoutTransition implements PatronRequestStateTransition {

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<PatronRequest.Status> possibleSourceStatus = List.of(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY, PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY);

	public ExpeditedCheckoutTransition(PatronRequestRepository patronRequestRepository,
																		 PatronRequestAuditService patronRequestAuditService)
	{
		this.patronRequestRepository = patronRequestRepository;
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
		// Should be applicable if it is a request of any workflow in REQUEST_PLACED_AT_BORROWING_AGENCY OR a RET_LOCAL workflow request at REQUEST_PLACED_AT_SUPPLYING_AGENCY
// Restore this if it causes issues removing it, check PICKUP_AGENCY as well
		final boolean isStatusApplicable = possibleSourceStatus.contains(ctx.getPatronRequest().getStatus()) || ctx.getPatronRequest().getStatus() == PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY  && ctx.getPatronRequest().getActiveWorkflow().equals("RET-LOCAL") ;
//		final boolean isStatusApplicable = possibleSourceStatus.contains(ctx.getPatronRequest().getStatus()) ;

		log.debug("Status applicable? {}", isStatusApplicable);
		log.debug("Expedited checkout? {}", ctx.getPatronRequest().getIsExpeditedCheckout());

		final boolean isExpeditedCheckout = ctx.getPatronRequest().getIsExpeditedCheckout() != null && ctx.getPatronRequest().getIsExpeditedCheckout();

		return isStatusApplicable && isExpeditedCheckout;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("Execute action: ExpeditedCheckoutTransition... for patron request in status {}", ctx.getPatronRequest().getStatus());

		String[] patronBarcodes;
		String auditBarcodeMessage;
		HashMap<String, Object> auditData = new HashMap<>();
		// Safely get the local barcode string using Optional
		String localBarcodeStr = Optional.ofNullable(ctx.getPatronVirtualIdentity())
			.map(PatronIdentity::getLocalBarcode)
			.orElse(null);

		if (localBarcodeStr != null) {
			patronBarcodes = extractPatronBarcodes(localBarcodeStr);
			auditBarcodeMessage = "Patron Barcodes: " + String.join(", ", patronBarcodes);
			log.debug("Extracted patron barcodes: {}", auditBarcodeMessage);
		} else {
			auditBarcodeMessage = "No patron identity or local barcodes available";
			log.debug("{} for request {}", auditBarcodeMessage, ctx.getPatronRequest().getId());
		}
		auditData.put("patronBarcodes", auditBarcodeMessage);

		// If any RET-LOCAL requests do accidentally end up here, send them on their way
		// We might not need this handling - investigate and restore patron_barcodes also so the patron barcode is logged in audit
		if (ctx.getPatronRequest().getActiveWorkflow() != null && ctx.getPatronRequest().getActiveWorkflow().equals("RET-LOCAL")) {
			ctx.getPatronRequest().setStatus(PatronRequest.Status.CONFIRMED);
			ctx.getWorkflowMessages().add("Expedited checkout completed for RET-LOCAL: re-routing");
			// Create audit entry with workflow messages
			auditData.put("workflowMessages", ctx.getWorkflowMessages());
			auditData.put("expeditedCheckout", true);
			auditData.put("sourceStatus", ctx.getPatronRequestStateOnEntry());
			// Back on track to be HANDED_OFF_AS_LOCAL
			return patronRequestAuditService.addAuditEntry(
					ctx.getPatronRequest(),
					"Expedited checkout completed for RET-LOCAL: setting to CONFIRMED to trigger standard RET-LOCAL workflow",
					auditData)
				.then(updatePatronRequest(ctx));
		}
		else
		{
			// Standard workflow. Progress to LOANED.
			ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED);
			ctx.getWorkflowMessages().add("Expedited checkout completed - item status changed to LOANED");
			// Create audit entry with workflow messages
			auditData.put("workflowMessages", ctx.getWorkflowMessages());
			auditData.put("expeditedCheckout", true);
			auditData.put("sourceStatus", ctx.getPatronRequestStateOnEntry());
			return patronRequestAuditService.addAuditEntry(
					ctx.getPatronRequest(),
					"ExpeditedCheckoutTransition has completed",
					auditData)
				.then(updatePatronRequest(ctx));
		}
	}


	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.LOANED);
	}

	@Override
	public List<PatronRequest.Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public boolean attemptAutomatically() {
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
