package org.olf.dcb.request.workflow;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * ResolveNextSupplierTransition - Called in response to a request in state NOT_SUPPLIED_CURRENT_SUPPLIER
 * the needed action is to see if there are other possible suppliers for which there is not an outstanding
 * supplier request, and if so, re-enter the workflow at the PlaceRequestAtSupplyingAgency step with that
 * new provider
 */
@Slf4j
@Singleton
@Named("ResolveNextSupplier")
public class ResolveNextSupplierTransition extends AbstractPatronRequestStateTransition
	implements PatronRequestStateTransition {

	private final HostLmsService hostLmsService;

	ResolveNextSupplierTransition(HostLmsService hostLmsService) {
		super(List.of(NOT_SUPPLIED_CURRENT_SUPPLIER));

		this.hostLmsService = hostLmsService;
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		return true;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return Mono.just(context)
			.flatMap(this::cancelLocalBorrowingRequest)
			.flatMap(this::markNoItemsAvailableAtAnyAgency);
	}

	private Mono<RequestWorkflowContext> cancelLocalBorrowingRequest(
		RequestWorkflowContext requestWorkflowContext) {

		final var borrowingHostLmsCode = getValue(requestWorkflowContext,
			RequestWorkflowContext::getPatronSystemCode, null);

		if (isEmpty(borrowingHostLmsCode)) {
			return Mono.error(new RuntimeException("Patron is not associated with a Host LMS"));
		}

		final var patronRequest = getValue(requestWorkflowContext,
			RequestWorkflowContext::getPatronRequest, null);

		final var localRequestStatus = getValue(patronRequest,
			PatronRequest::getLocalRequestStatus, "");

		if (localRequestStatus.equals(HOLD_MISSING) || localRequestStatus.equals(HOLD_CANCELLED)) {
			return Mono.just(requestWorkflowContext);
		}

		final var homePatronIdentity = getValue(requestWorkflowContext, RequestWorkflowContext::getPatronHomeIdentity, null);

		final var localRequestId = getValue(patronRequest, PatronRequest::getLocalRequestId, null);
		final var localItemId = getValue(patronRequest, PatronRequest::getLocalItemId, null);
		final var localPatronId = getValue(homePatronIdentity, PatronIdentity::getLocalId, null);

		return hostLmsService.getClientFor(borrowingHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(localRequestId)
				.localItemId(localItemId)
				.patronId(localPatronId)
				.build()))
			.thenReturn(requestWorkflowContext);
	}

	private Mono<RequestWorkflowContext> markNoItemsAvailableAtAnyAgency(RequestWorkflowContext context) {
		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);

		return Mono.just(context.setPatronRequest(
			patronRequest.setStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY)));
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "ResolveNextSupplierTransition";
	}

	@Override
	public List<DCBGuardCondition> getGuardConditions() {
		return List.of(new DCBGuardCondition("DCBRequestStatus is (NOT_SUPPLIED_CURRENT_SUPPLIER)"));
	}

	@Override
	public List<DCBTransitionResult> getOutcomes() {
		return List.of(new DCBTransitionResult("NO_ITEMS_AVAILABLE_AT_ANY_AGENCY", NO_ITEMS_AVAILABLE_AT_ANY_AGENCY.toString()));
	}
}
