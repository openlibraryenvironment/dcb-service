package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.CANCELLED;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Named("SupplierRequestCancelled")
public class HandleSupplierRequestCancelled extends AbstractPatronRequestStateTransition
	implements PatronRequestStateTransition {

	private final SupplierRequestService supplierRequestService;
	private final PatronRequestAuditService patronRequestAuditService;

	HandleSupplierRequestCancelled(SupplierRequestService supplierRequestService,
		PatronRequestAuditService patronRequestAuditService) {

		super(List.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED,
			REQUEST_PLACED_AT_BORROWING_AGENCY, REQUEST_PLACED_AT_PICKUP_AGENCY));

		this.supplierRequestService = supplierRequestService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		final var localRequestStatus = getValue(context,
			RequestWorkflowContext::getSupplierRequest,
				SupplierRequest::getLocalStatus, "");

		final var applicableStatus = localRequestStatus.equals(HOLD_CANCELLED)
			|| localRequestStatus.equals(HOLD_MISSING);

		final var isActive = getValue(context,
			RequestWorkflowContext::getSupplierRequest,
			SupplierRequest::getIsActive, true);

		return applicableStatus && isActive;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return Mono.just(context)
			.flatMap(this::markNotSuppliedByCurrentSupplier)
			.flatMap(this::cancelSupplierRequest)
			.flatMap(this::auditCancellation);
	}

	private Mono<RequestWorkflowContext> auditCancellation(RequestWorkflowContext context) {
		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);
		final var supplierRequest = getValue(context,
			RequestWorkflowContext::getSupplierRequest, null);

		final var supplierRequestId = getValue(supplierRequest, SupplierRequest::getId,
			Object::toString, "Unknown");

		final var auditData = new HashMap<String, Object>();

		auditData.put("localRequestStatus", getValue(supplierRequest,
			SupplierRequest::getLocalStatus, "Unknown"));
		auditData.put("supplierHostLmsCode", getValue(supplierRequest, SupplierRequest::getHostLmsCode, "Unknown"));
		auditData.put("localItemLocationCode", getValue(supplierRequest, SupplierRequest::getLocalItemLocationCode, "Unknown"));
		auditData.put("supplierAgencyCode", getValue(supplierRequest, SupplierRequest::getLocalAgency, "Unknown"));

		return patronRequestAuditService.addAuditEntry(patronRequest,
				"Supplier Request Cancelled (ID: \"%s\")".formatted(supplierRequestId), auditData)
			.thenReturn(context);
	}

	private Mono<RequestWorkflowContext> markNotSuppliedByCurrentSupplier(RequestWorkflowContext context) {
		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);

		return Mono.just(context.setPatronRequest(
			patronRequest.setStatus(NOT_SUPPLIED_CURRENT_SUPPLIER)));
	}

	private Mono<RequestWorkflowContext> cancelSupplierRequest(RequestWorkflowContext context) {
		final var supplierRequest = getValue(context,
			RequestWorkflowContext::getSupplierRequest, null);

		return Mono.just(supplierRequest.setStatusCode(CANCELLED))
			.flatMap(supplierRequestService::updateSupplierRequest)
			.map(context::setSupplierRequest);
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(NOT_SUPPLIED_CURRENT_SUPPLIER);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "HandleSupplierRequestCancelled";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBRequestStatus is (REQUEST_PLACED_AT_SUPPLYING_AGENCY OR CONFIRMED OR REQUEST_PLACED_AT_BORROWING_AGENCY)" +
			"supplying request local status is (MISSING OR CANCELLED)"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("NOT_SUPPLIED_CURRENT_SUPPLIER", NOT_SUPPLIED_CURRENT_SUPPLIER.toString()));
	}
}
