package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.AVAILABLE;

import reactor.core.publisher.Mono;

import java.util.Map;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.core.model.PatronRequest;
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
public class HandleBorrowerItemLoaned implements WorkflowAction {
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	public HandleBorrowerItemLoaned(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Transactional
	public Mono<Map<String, Object>> execute(Map<String, Object> context) {
		StateChange sc = (StateChange) context.get("StateChange");
		log.debug("HandleBorrowerLoaned {}", sc);
		PatronRequest pr = (PatronRequest) sc.getResource();
		if (pr != null) {
			pr.setStatus(PatronRequest.Status.LOANED);
			pr.setLocalItemStatus(sc.getToState());

			return requestWorkflowContextHelper.fromPatronRequest(pr)
				.flatMap(this::checkHomeItemOutToVirtualPatron)
				.flatMap(rwc -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
				.doOnNext(spr -> log.debug("Saved {}", spr))
				.doOnError(
					error -> log.error("Error occurred in handle item Loaned: ", error))
				.thenReturn(context);
		} else {
			log.warn("Unable to locate patron request to mark as available");
			return Mono.just(context);
		}
	}


	public Mono<RequestWorkflowContext> checkHomeItemOutToVirtualPatron(
		RequestWorkflowContext rwc) {

		if ((rwc.getSupplierRequest() != null) &&
			(rwc.getSupplierRequest().getLocalItemId() != null) &&
			(rwc.getLenderSystemCode() != null) &&
			(rwc.getPatronVirtualIdentity() != null)) {

			// In some systems a patron can have multiple barcodes. In those systems getLocalBarcode will be encoded as [value, value, value]
			// So we trim the opening and closing [] and split on the ", " Otherwise just split on ", " just in case
			final String[] patron_barcodes = extractPatronBarcodes(
				rwc.getPatronVirtualIdentity().getLocalBarcode());

			if ((patron_barcodes != null) && (patron_barcodes.length > 0)) {

				String home_item_barcode = rwc.getSupplierRequest().getLocalItemBarcode();

				log.info("Update check home item out : {} to {} at {}",
					home_item_barcode, patron_barcodes[0], rwc.getLenderSystemCode());

				return hostLmsService.getClientFor(rwc.getLenderSystemCode())
					.flatMap(hostLmsClient -> updateThenCheckoutItem(rwc, hostLmsClient,
						patron_barcodes))
					.flatMap(srwc -> patronRequestAuditService.addAuditEntry(
						srwc.getPatronRequest(),
						"Home item (b=" + home_item_barcode + "@" + rwc.getLenderSystemCode() + ") checked out to virtual patron (b=" + patron_barcodes[0] + ")"))
					.doOnError(error -> patronRequestAuditService.addErrorAuditEntry(
							rwc.getPatronRequest(), "Error attempting to check out home item to vpatron:" + error))
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
						"Missing data attempting to set home item off campus {} {} {}",
						rwc, rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity()))
				.thenReturn(rwc);
		}
	}

	private Mono<RequestWorkflowContext> updateThenCheckoutItem(
		RequestWorkflowContext rwc, HostLmsClient hostLmsClient, String[] patronBarcode) {

		final var supplierRequest = rwc.getSupplierRequest();

		return hostLmsClient.updateItemStatus(supplierRequest.getLocalItemId(),
				AVAILABLE, supplierRequest.getLocalId())
			.then(hostLmsClient.checkOutItemToPatron(rwc.getSupplierRequest().getLocalItemBarcode(),
				patronBarcode[0], supplierRequest.getLocalId()))
			.thenReturn(rwc);
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
}
