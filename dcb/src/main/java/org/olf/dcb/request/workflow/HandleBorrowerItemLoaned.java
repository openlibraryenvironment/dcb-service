package org.olf.dcb.request.workflow;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;

import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import reactor.core.publisher.Mono;

import java.util.*;

import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Named("BorrowerRequestLoaned")
public class HandleBorrowerItemLoaned implements PatronRequestStateTransition {
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(Status.READY_FOR_PICKUP);
	private static final List<String> triggeringItemStates = List.of(HostLmsItem.ITEM_LOANED);
	
	public HandleBorrowerItemLoaned(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {

		final var isPossibleSourceStatus = getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
		final var isTriggeringLocalItemStatus = Optional.ofNullable(ctx.getPatronRequest().getLocalItemStatus())
			.map(triggeringItemStates::contains)
			.orElse(false);
		final var isTriggeringPickupItemStatus = Optional.ofNullable(ctx.getPatronRequest().getPickupItemStatus())
			.map(triggeringItemStates::contains)
			.orElse(false);

		return isPossibleSourceStatus && (isTriggeringLocalItemStatus || isTriggeringPickupItemStatus);
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		log.info("Execute action: HandleBorrowerItemLoaned...");
		ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED);

		return checkHomeItemOutToVirtualPatron(ctx)
			.flatMap(this::handlePUAWorkflow)
			.flatMap(this::updatePatronRequest);
	}

	private Mono<RequestWorkflowContext> handlePUAWorkflow(RequestWorkflowContext ctx) {
		if ("RET-PUA".equals(ctx.getPatronRequest().getActiveWorkflow())) {
			log.debug("PUA workflow");

			if (Optional.ofNullable(ctx.getPatronRequest().getLocalItemStatus())
				.map(triggeringItemStates::contains)
				.orElse(false)) {
				log.warn("PUA workflow, skipping home item checkout to local patron");

				return Mono.just(ctx);
			}

			return checkHomeItemOutToLocalPatron(ctx);
		}

		return Mono.just(ctx);
	}

	public Mono<RequestWorkflowContext> checkHomeItemOutToVirtualPatron(
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

				log.info("Update check home item out : {} to {} at {}",
					home_item_barcode, patron_barcodes[0], rwc.getLenderSystemCode());

				return hostLmsService.getClientFor(rwc.getLenderSystemCode())
					.flatMap(hostLmsClient -> checkoutItemToPatronIfEnabled(rwc, hostLmsClient, patron_barcodes))
					.doOnNext(srwc -> {
						String homeItemBarcode = Objects.toString(home_item_barcode, "unknown");
						String lenderSystemCode = Objects.toString(rwc.getLenderSystemCode(), "unknown");
						String patronBarcode =  Objects.toString(patron_barcodes[0], "unknown");
						String message = String.format("Home item (b=%s@%s) checked out to virtual patron (b=%s)",
							homeItemBarcode, lenderSystemCode, patronBarcode
						);
						rwc.getWorkflowMessages().add(message);
					})
					.onErrorResume(error -> {
						log.error("Problem checking out item {} to vpatron {}", home_item_barcode, patron_barcodes, error);

						var auditData = new HashMap<String, Object>();
						auditData.put("virtual-patron-barcode", Arrays.toString(patron_barcodes));
						auditData.put("home-item-barcode", home_item_barcode);
						auditData.put("lender-system-code", rwc.getLenderSystemCode());
						auditThrowable(auditData, "Throwable", error);

						// Intentionally transform Error
						// A virtual checkout is deemed as more of a notification than a critical action
						return patronRequestAuditService
							.addAuditEntry(rwc.getPatronRequest(), "Virtual checkout failed : " + error.getMessage(), auditData)
							.thenReturn(rwc);
					})
					.thenReturn(rwc);
			} else {

				log.error(
					"NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT {}",
					rwc.getPatronVirtualIdentity().getLocalBarcode());

				return patronRequestAuditService.addErrorAuditEntry(
						rwc.getPatronRequest(),
						"NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT")
					.thenReturn(rwc);
			}
		} else {
			log.error("Missing data attempting to set home item off campus {} {} {}",
				rwc, rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity());
			return patronRequestAuditService.addErrorAuditEntry(
					rwc.getPatronRequest(),
					String.format(
						"Missing data attempting to set home item off campus %s %s %s",
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
				.doOnNext(resp -> log.debug("checkOutItemToPatron returned {}", resp))
				.thenReturn(rwc);
		}
		else {
			rwc.getWorkflowMessages().add("reflectPatronLoanAtSupplier disabled for this client");
			return Mono.just(rwc);
		}
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

	public Mono<RequestWorkflowContext> checkHomeItemOutToLocalPatron(RequestWorkflowContext rwc) {
		log.debug("checkHomeItemOutToLocalPatron");

		final var patronRequest = rwc.getPatronRequest();

		if (!hasRequiredCheckoutData(rwc)) {
			return handleMissingCheckoutData(rwc);
		}

		final String[] patronBarcodes = extractPatronBarcodes(rwc.getPatronHomeIdentity().getLocalBarcode());

		if (patronBarcodes == null || patronBarcodes.length == 0) {
			return handleMissingPatronBarcode(rwc);
		}

		final var localItemId = patronRequest.getLocalItemId();
		final var command = buildCheckoutCommand(rwc, patronBarcodes[0], localItemId);

		return hostLmsService.getClientFor(rwc.getPatronSystemCode())
			.flatMap(hostLmsClient -> hostLmsClient.checkOutItemToPatron(command))
			.map(ok -> logSuccessfulCheckout(rwc, localItemId, patronBarcodes[0]))
			.onErrorResume(error -> handleCheckoutError(error, rwc, localItemId, patronBarcodes));
	}

	private boolean hasRequiredCheckoutData(RequestWorkflowContext rwc) {
		return rwc.getPatronRequest().getLocalItemId() != null
			&& rwc.getPatronSystemCode() != null
			&& rwc.getPatronHomeIdentity() != null;
	}

	private CheckoutItemCommand buildCheckoutCommand(RequestWorkflowContext rwc, String patronBarcode, String localItemId) {
		return CheckoutItemCommand.builder()
			.itemId(localItemId)
			.patronId(rwc.getPatronHomeIdentity().getLocalId())
			.patronBarcode(patronBarcode)
			.localRequestId(rwc.getPatronRequest().getLocalRequestId())
			.libraryCode(rwc.getPatronHomeIdentity().getLocalHomeLibraryCode())
			.build();
	}

	private RequestWorkflowContext logSuccessfulCheckout(RequestWorkflowContext rwc, String localItemId, String patronBarcode) {
		String patronSystemCode = Objects.toString(rwc.getPatronSystemCode(), "unknown");
		String message = String.format("Home item (b=%s@%s) checked out to home patron (b=%s)",
			localItemId, patronSystemCode, patronBarcode);
		rwc.getWorkflowMessages().add(message);

		return rwc;
	}

	private Mono<RequestWorkflowContext> handleCheckoutError(Throwable error, RequestWorkflowContext rwc,
		String localItemId, String[] patronBarcodes) {
		log.error("Problem checking out item {} to home patron {}", localItemId, patronBarcodes, error);

		var auditData = new HashMap<String, Object>();
		auditData.put("patron-barcode", Arrays.toString(patronBarcodes));
		auditData.put("home-item-id", localItemId);
		auditData.put("patron-system-code", rwc.getPatronSystemCode());
		auditThrowable(auditData, "Throwable", error);

		// Intentionally transform Error
		// A virtual checkout is deemed as more of a notification than a critical action
		return patronRequestAuditService
			.addAuditEntry(rwc.getPatronRequest(), "Patron checkout failed : " + error.getMessage(), auditData)
			.thenReturn(rwc);
	}

	private Mono<RequestWorkflowContext> handleMissingCheckoutData(RequestWorkflowContext rwc) {
		log.error("Missing data attempting to set home item off campus. RequestWorkflowContext: {}, PatronRequest ID: {}, PatronHomeIdentity: {}",
			rwc, rwc.getPatronRequest().getId(), rwc.getPatronHomeIdentity());

		final var auditMessage = String.format(
			"Missing data attempting to set home item off campus. RequestWorkflowContext: %s, PatronRequest ID: %s, PatronHomeIdentity: %s",
			rwc, rwc.getPatronRequest().getId(), rwc.getPatronHomeIdentity());

		return patronRequestAuditService.addErrorAuditEntry(rwc.getPatronRequest(), auditMessage)
			.thenReturn(rwc);
	}

	private Mono<RequestWorkflowContext> handleMissingPatronBarcode(RequestWorkflowContext rwc) {
		log.error("NO BARCODE FOR PATRON HOME IDENTITY. UNABLE TO CHECK OUT {}",
			rwc.getPatronHomeIdentity().getLocalBarcode());

		return patronRequestAuditService.addErrorAuditEntry(
				rwc.getPatronRequest(),
				"NO BARCODE FOR PATRON HOME IDENTITY. UNABLE TO CHECK OUT")
			.thenReturn(rwc);
	}

	private Mono<RequestWorkflowContext> updatePatronRequest(RequestWorkflowContext requestWorkflowContext) {
		return Mono.from(patronRequestRepository.saveOrUpdate(requestWorkflowContext.getPatronRequest()))
			.thenReturn(requestWorkflowContext);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}
	
	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(PatronRequest.Status.LOANED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleBorrowerItemLoaned";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBRequestStatus is ( READY_FOR_PICKUP OR RECEIVED_AT_PICKUP OR PICKUP_TRANSIT ) AND ItemStatus is LOANED"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("LOANED",PatronRequest.Status.LOANED.toString()));
	}
}
