package org.olf.dcb.request.workflow;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;

@Slf4j
@Singleton
@Named("ExpeditedCheckoutTransition")
// Can only occur when the supplying agency and the pickup agency are the same

public class ExpeditedCheckoutTransition implements PatronRequestStateTransition {

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	private final HostLmsService hostLmsService;

	private static final List<PatronRequest.Status> possibleSourceStatus = List.of(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY, PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY);

	public ExpeditedCheckoutTransition(PatronRequestRepository patronRequestRepository,
																		 PatronRequestAuditService patronRequestAuditService,
																		 HostLmsService hostLmsService)
	{
		this.patronRequestRepository = patronRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
		this.hostLmsService = hostLmsService;
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
		// Expedited checkout requests will always have the same supplier and pickup system, so will always be in the RET-EXP workflow
		final boolean isStatusApplicable = possibleSourceStatus.contains(ctx.getPatronRequest().getStatus());
		final boolean isExpeditedCheckout = ctx.getPatronRequest().getIsExpeditedCheckout() != null && ctx.getPatronRequest().getIsExpeditedCheckout();
		final boolean isWorkflow =   ctx.getPatronRequest().getActiveWorkflow() != null &&  ctx.getPatronRequest().getActiveWorkflow().equals("RET-EXP");
		return isStatusApplicable && (isExpeditedCheckout || isWorkflow);
//		return isStatusApplicable && isExpeditedCheckout;
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
			auditBarcodeMessage = "Expedited checkout: No patron identity or local barcodes available";
			log.debug("{} for request {}", auditBarcodeMessage, ctx.getPatronRequest().getId());
		}
		auditData.put("patronBarcodes", auditBarcodeMessage);

		// Checkout at supplier
		return checkoutToVisitingPatron(ctx)
			.flatMap(this::updatePatronRequest);
		// Explicityl set loaned on success
		//		ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED);
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

	public Mono<RequestWorkflowContext> checkoutToVisitingPatron(
		RequestWorkflowContext rwc) {

		if ((rwc.getSupplierRequest() != null) &&
			(rwc.getSupplierRequest().getLocalItemId() != null) &&
			(rwc.getLenderSystemCode() != null) &&
			(rwc.getPatronVirtualIdentity() != null)) {

			// In some systems a patron can have multiple barcodes. In those systems getLocalBarcode will be encoded as [value, value, value]
			// So we trim the opening and closing [] and split on the ", " Otherwise just split on ", " just in case
			final String[] patron_barcodes = extractPatronBarcodes(rwc.getPatronVirtualIdentity().getLocalBarcode());

			if ((patron_barcodes != null) && (patron_barcodes.length > 0)) {

				String home_item_barcode = rwc.getSupplierRequest().getLocalItemBarcode();

				log.info("Update check home item out for expedited checkout : {} to {} at {}",
					home_item_barcode, patron_barcodes[0], rwc.getLenderSystemCode());

				return hostLmsService.getClientFor(rwc.getLenderSystemCode())
					.flatMap(hostLmsClient -> checkoutItemToPatronIfEnabled(rwc, hostLmsClient, patron_barcodes))
					.doOnNext(srwc -> {
						String homeItemBarcode = Objects.toString(home_item_barcode, "unknown");
						String lenderSystemCode = Objects.toString(rwc.getLenderSystemCode(), "unknown");
						String patronBarcode =  Objects.toString(patron_barcodes[0], "unknown");
						String message = String.format("Home item (b=%s@%s) checked out to visiting patron in expedited checkout (b=%s)",
							homeItemBarcode, lenderSystemCode, patronBarcode
						);
						rwc.getWorkflowMessages().add(message);
					})
					.onErrorResume(error -> {
						log.error("Problem: Expedited checkout failed for item {} to vpatron {}", home_item_barcode, patron_barcodes, error);

						var auditData = new HashMap<String, Object>();
						auditData.put("virtual-patron-barcode", Arrays.toString(patron_barcodes));
						auditData.put("home-item-barcode", home_item_barcode);
						auditData.put("lender-system-code", rwc.getLenderSystemCode());
						auditThrowable(auditData, "Throwable", error);

						// Intentionally transform Error
						// Consider whether this transformation is necessary in expedited checkout.
						// As if a virtual checkout fails here the whole thing could fall down.
						return patronRequestAuditService
							.addAuditEntry(rwc.getPatronRequest(), "Expedited checkout failed : " + error.getMessage(), auditData)
							.thenReturn(rwc);
					})
					.thenReturn(rwc);
			} else {

				log.error(
					"EXPEDITED CHECKOUT: NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT {}",
					rwc.getPatronVirtualIdentity().getLocalBarcode());

				return patronRequestAuditService.addErrorAuditEntry(
						rwc.getPatronRequest(),
						"Expedited Checkout: NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT")
					.thenReturn(rwc);
			}
		} else {
			log.error("EXPEDITED CHECKOUT: Missing data attempting to set home item off campus {} {} {}",
				rwc, rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity());
			return patronRequestAuditService.addErrorAuditEntry(
					rwc.getPatronRequest(),
					String.format(
						"Expedited Checkout: Missing data attempting to set home item off campus %s %s %s",
						rwc, rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity()))
				.thenReturn(rwc);
		}
	}


	private Mono<RequestWorkflowContext> checkoutItemToPatronIfEnabled(
		RequestWorkflowContext rwc, HostLmsClient hostLmsClient, String[] patronBarcode) {

		final var supplierRequest = rwc.getSupplierRequest();

		if ( hostLmsClient.reflectPatronLoanAtSupplier() ) {

			final var command = CheckoutItemCommand.builder()
				.itemId(supplierRequest.getLocalItemId())
				.itemBarcode(supplierRequest.getLocalItemBarcode())
				.patronId(rwc.getPatronVirtualIdentity().getLocalId())
				.patronBarcode(patronBarcode[0])
				.localRequestId(supplierRequest.getLocalId())
				.libraryCode(supplierRequest.getLocalItemLocationCode())
				.build();

			return hostLmsClient.checkOutItemToPatron(command)
				.doOnNext(resp -> log.debug("Expedited checkout: checkOutItemToPatron returned {}", resp))
				.thenReturn(rwc);
		}
		else {
			rwc.getWorkflowMessages().add("Expedited checkout: reflectPatronLoanAtSupplier disabled for this client");
			return Mono.just(rwc);
		}
	}
}
