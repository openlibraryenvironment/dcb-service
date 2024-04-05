package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.AVAILABLE;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.storage.PatronRequestRepository;
import jakarta.transaction.Transactional;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Named("BorrowerRequestLoaned")
public class HandleBorrowerItemLoaned implements PatronRequestStateTransition {
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	private static final List<Status> possibleSourceStatus = List.of(Status.READY_FOR_PICKUP);
	private static final List<String> triggeringItemStates = List.of(HostLmsItem.ITEM_LOANED);
	
	public HandleBorrowerItemLoaned(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestAuditService = patronRequestAuditService;
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
					.flatMap(hostLmsClient -> updateThenCheckoutItem(rwc, hostLmsClient, patron_barcodes))
					.doOnNext(srwc -> rwc.getWorkflowMessages().add("Home item (b=" + home_item_barcode + "@" + rwc.getLenderSystemCode() + ") checked out to virtual patron (b=" + patron_barcodes[0] + ")") )
					.doOnError(error -> {
						log.error("problem checking out item {} to vpatron {}: {}",home_item_barcode, patron_barcodes, error);
						patronRequestAuditService.addErrorAuditEntry( rwc.getPatronRequest(), "Error attempting to check out home item to vpatron:" + error);
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

	private Mono<RequestWorkflowContext> updateThenCheckoutItem(
		RequestWorkflowContext rwc, HostLmsClient hostLmsClient, String[] patronBarcode) {

		final var supplierRequest = rwc.getSupplierRequest();

		if ( hostLmsClient.reflectPatronLoanAtSupplier() ) {
			return hostLmsClient.updateItemStatus(supplierRequest.getLocalItemId(), AVAILABLE, supplierRequest.getLocalId())
				.then(hostLmsClient.checkOutItemToPatron(
					supplierRequest.getLocalItemId(), 
					supplierRequest.getLocalItemBarcode(),
					rwc.getPatronVirtualIdentity().getLocalId(),
					patronBarcode[0], 
					supplierRequest.getLocalId()))

				// .then(hostLmsClient.checkOutItemToPatron(rwc.getSupplierRequest().getLocalItemBarcode(), patronBarcode[0], supplierRequest.getLocalId()))
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

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ( getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus()) &&
			( triggeringItemStates.contains(ctx.getPatronRequest().getLocalItemStatus() ) ) );
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		log.info("Execute action: HandleBorrowerItemLoaned...");
		ctx.getPatronRequest().setStatus(PatronRequest.Status.LOANED);

		return this.checkHomeItemOutToVirtualPatron(ctx)
			// For now, PatronRequestWorkflowService will save te patron request, but we should do that here
			// and not there - flagging this as a change needed when we refactor.
			.thenReturn(ctx);
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
