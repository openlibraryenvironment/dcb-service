package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.WorkflowConstants.EXPEDITED_WORKFLOW;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("ExpeditedCheckoutTransition")
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

	private static String firstBarcode(String localBarcode) {
		final var barcodes = extractPatronBarcodes(localBarcode);

		return barcodes != null && barcodes.length > 0 ? barcodes[0] : null;
	}

	private static String[] extractPatronBarcodes(String inputstr) {
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

	// The flag alone is not enough: a request carrying it that resolved to another workflow has
	// its item still on the supplier's shelf, and checking it out there would record a loan
	// that never happened
	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();

		return possibleSourceStatus.contains(patronRequest.getStatus())
			&& Boolean.TRUE.equals(patronRequest.getIsExpeditedCheckout())
			&& EXPEDITED_WORKFLOW.equals(patronRequest.getActiveWorkflow());
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("Execute action: ExpeditedCheckoutTransition for patron request {} in status {}",
			ctx.getPatronRequest().getId(), ctx.getPatronRequest().getStatus());

		// The supplier checkout is the loan: the patron is at that desk with the item in their hand,
		// so it runs first and a refusal stops the request before anything is recorded at the
		// borrower. The borrower checkout mirrors the loan in the patron's own library and is
		// allowed to fail without denying the loan that did happen.
		return checkoutAtSupplier(ctx)
			.flatMap(this::checkoutAtBorrower)
			.map(rwc -> {
				rwc.getPatronRequest().setStatus(PatronRequest.Status.LOANED);
				return rwc;
			})
			.flatMap(this::updatePatronRequest)
			.doOnError(error -> log.error("Expedited checkout failed for patron request {}",
				ctx.getPatronRequest().getId(), error));
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

		// The borrower's own patron and virtual item, with the barcode that item carries: the
		// virtual identity belongs to the supplier, and an ILS that finds items by barcode - Alma
		// does - cannot act on a command carrying neither
		final var command = CheckoutItemCommand.builder()
			.localRequestId(localRequestId) // Crucially, this is the borrower's transaction ID
			.itemId(patronRequest.getLocalItemId())
			.itemBarcode(getValueOrNull(rwc, RequestWorkflowContext::getSupplierRequest,
				SupplierRequest::getLocalItemBarcode))
			.patronId(getValueOrNull(rwc, RequestWorkflowContext::getPatronHomeIdentity,
				PatronIdentity::getLocalId))
			.patronBarcode(firstBarcode(getValueOrNull(rwc,
				RequestWorkflowContext::getPatronHomeIdentity, PatronIdentity::getLocalBarcode)))
			.libraryCode(getValueOrNull(rwc, RequestWorkflowContext::getPatronHomeIdentity,
				PatronIdentity::getLocalHomeLibraryCode))
			.build();

		return hostLmsService.getClientFor(borrowerSystemCode)
			.flatMap(hostLmsClient -> hostLmsClient.checkOutItemToPatron(command))
			.doOnSuccess(response -> log.debug("Successfully performed expedited checkout at borrower LMS."))
			.thenReturn(rwc)
			.onErrorResume(error -> {
			log.error("An error has occurred with the borrower-side expedited checkout", error);
			// The loan still happens at the supplier; the patron's own library just has no record of it
			rwc.getPatronRequest().setNeedsAttention(Boolean.TRUE);

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
					.then(Mono.error(error));
			});
	}
}

