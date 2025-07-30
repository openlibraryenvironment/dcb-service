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
			auditBarcodeMessage = "Expedited checkout: No patron identity or local barcodes available";
			log.debug("{} for request {}", auditBarcodeMessage, ctx.getPatronRequest().getId());
		}
		auditData.put("patronBarcodes", auditBarcodeMessage);

		return checkoutAtBorrower(ctx)
			.then(checkoutAtSupplier(ctx))
			.flatMap(this::updatePatronRequest)
			// Explicitly set loaned on success to try and break out of the expedited checkout loop
			.doOnSuccess(patronRequest -> ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED))
			.doOnError(error -> log.error("Expedited checkout failed.", error));
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

	/**
	 * Performs the expedited checkout at the borrower LMS.
	 */

	private Mono<RequestWorkflowContext> checkoutAtBorrower(RequestWorkflowContext rwc) {
		final var patronRequest = rwc.getPatronRequest();
		final String borrowerSystemCode = patronRequest.getPatronHostlmsCode();
		final String localRequestId = patronRequest.getLocalRequestId(); // The BORROWER's request ID
		if (borrowerSystemCode == null || localRequestId == null) {
			log.error("Missing borrower system code or local request ID for expedited checkout.");
			return Mono.error(new IllegalStateException("Cannot perform checkout at borrower: missing system code or request ID."));
		}

		log.info("Attempting expedited checkout at BORROWER system: {}", borrowerSystemCode);

		final var command = CheckoutItemCommand.builder()
			.localRequestId(localRequestId) // Crucially, this is the borrower's transaction ID
			.patronId(rwc.getPatronVirtualIdentity().getLocalId())
			.build();

		return hostLmsService.getClientFor(borrowerSystemCode)
			.flatMap(hostLmsClient -> hostLmsClient.checkOutItemToPatron(command))
			.doOnSuccess(response -> log.debug("Successfully performed expedited checkout at borrower LMS."))
			.thenReturn(rwc)
			.onErrorResume(error -> {
			log.error("An error has occurred with the borrower-side expedited checkout", error);
			return patronRequestAuditService
				.addAuditEntry(rwc.getPatronRequest(), "Expedited checkout at borrower failed: " + error.getMessage())
				.thenReturn(rwc);
		});
	}

	/**
	 * Performs the expedited checkout at the supplier LMS.
	 * Takes into account the 'reflectLoanAtSupplier' status: won't complete without this being true.
	 */

	private Mono<RequestWorkflowContext> checkoutAtSupplier(RequestWorkflowContext rwc) {
		if (rwc.getSupplierRequest() == null || rwc.getLenderSystemCode() == null || rwc.getPatronVirtualIdentity() == null) {
			log.error("Missing supplier request, lender system code, or virtual patron identity for expedited checkout.");
			return Mono.error(new IllegalStateException("Cannot perform checkout at supplier: missing critical data."));
		}

		final var supplierRequest = rwc.getSupplierRequest();
		final String[] patronBarcodes = extractPatronBarcodes(rwc.getPatronVirtualIdentity().getLocalBarcode());

		if (patronBarcodes == null || patronBarcodes.length == 0) {
			log.error("No patron barcode available for virtual identity.");
			return Mono.error(new IllegalStateException("No patron barcode found for expedited checkout at supplier."));
		}

		return hostLmsService.getClientFor(rwc.getLenderSystemCode())
			.flatMap(hostLmsClient -> {
				if (hostLmsClient.reflectPatronLoanAtSupplier()) {
					final var command = CheckoutItemCommand.builder()
						.localRequestId(supplierRequest.getLocalId()) // The SUPPLIER's transaction ID
						.itemId(supplierRequest.getLocalItemId())
						.itemBarcode(supplierRequest.getLocalItemBarcode())
						.patronId(rwc.getPatronVirtualIdentity().getLocalId())
						.patronBarcode(patronBarcodes[0])
						.build();

					return hostLmsClient.checkOutItemToPatron(command);
				} else {
					log.warn("Reflecting loan at supplier is disabled for {}.", rwc.getLenderSystemCode());
					return Mono.error(new IllegalStateException("Cannot perform checkout at supplier: reflecting the loan at the supplier is disabled."));
				}
			})
			.doOnSuccess(response -> log.debug("Successfully checked out item at supplier LMS."))
			.thenReturn(rwc)
			.onErrorResume(error -> {
				log.error("An error has occurred with the supplier-side expedited checkout", error);
				return patronRequestAuditService
					.addAuditEntry(rwc.getPatronRequest(), "Expedited checkout at supplier failed: " + error.getMessage())
					.thenReturn(rwc);
			});
	}
}

