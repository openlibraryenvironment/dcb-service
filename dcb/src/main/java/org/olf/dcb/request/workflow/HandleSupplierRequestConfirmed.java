package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("SupplierRequestConfirmed")
public class HandleSupplierRequestConfirmed extends AbstractPatronRequestStateTransition
	implements PatronRequestStateTransition {

	private final SupplierRequestService supplierRequestService;
	private final HostLmsService hostLmsService;

	public HandleSupplierRequestConfirmed(
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		SupplierRequestService supplierRequestRepository,
		HostLmsService hostLmsService) {

		super(List.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		
		this.supplierRequestService = supplierRequestRepository;
		this.hostLmsService = hostLmsService;
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		final var localHoldStatus = getValue(context, RequestWorkflowContext::getSupplierRequest,
			SupplierRequest::getLocalStatus, "");

		// The local request may go into transit before DCB has a chance to detect confirmation
		// when that happens, infer that confirmation has happened
		return (localHoldStatus.equals(HOLD_CONFIRMED) || localHoldStatus.equals(HOLD_TRANSIT));
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		// The presence of a supplier is checked in `isApplicableFor` so not repeated here
		final var supplierRequest = ctx.getSupplierRequest();
		final var patronRequest = ctx.getPatronRequest();
		final var requestId = getValueOrNull(supplierRequest, SupplierRequest::getLocalId);
		final var supplierPatronId = getValueOrNull(supplierRequest, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(requestId).localPatronId(supplierPatronId).build();

		return hostLmsService.getClientFor(supplierRequest.getHostLmsCode())
			.flatMap(hostLmsClient -> hostLmsClient.getRequest(hostlmsRequest))
			.flatMap(localRequest -> {
				final var localItemId = localRequest.getRequestedItemId();
				final var localItemBarcode = localRequest.getRequestedItemBarcode();

				if (localItemId != null) {
					supplierRequest.setLocalItemId(localItemId);

					if (localItemBarcode != null) {
						// This could lead to an inconsistent barcode if the new item has no barcode
						// However FOLIO cannot provide the item ID or barcode when getting requests
						// Eager confirmation for FOLIO might mean this could be avoided
						supplierRequest.setLocalItemBarcode(localItemBarcode);
					}

					// This update could happen even if the item information hasn't changed from the original resolution
					return supplierRequestService.updateSupplierRequest(supplierRequest);
				}
				else {
					return Mono.just(supplierRequest);
				}
			})
			.flatMap(updatedSupplierRequest -> Mono.defer(() -> Mono.just(patronRequest.setStatus(CONFIRMED))))
			.then(Mono.just(ctx));
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(CONFIRMED);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierRequestConfirmed";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBPatronRequest status is REQUEST_PLACED_AT_SUPPLYING_AGENCY and Supplier Item Status is CONFIRMED"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("CONFIRMED","CONFIRMED"));
	}
}
