package org.olf.dcb.request.workflow;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLUTION;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.request.resolution.ResolutionParameters.parametersFor;
import static org.olf.dcb.request.resolution.SupplierRequestService.mapToSupplierRequest;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.statemodel.DCBGuardCondition;
import org.olf.dcb.statemodel.DCBTransitionResult;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

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
	private final PatronRequestAuditService patronRequestAuditService;
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;
	private final BeanProvider<SupplierRequestService> supplierRequestServiceProvider;
	private SupplierRequestService supplierRequestService;
	private final ConsortiumService consortiumService;

	public ResolveNextSupplierTransition(HostLmsService hostLmsService,
		PatronRequestAuditService patronRequestAuditService,
		PatronRequestResolutionService patronRequestResolutionService,
		BeanProvider<PatronRequestService> patronRequestServiceProvider,
		BeanProvider<SupplierRequestService> supplierRequestServiceProvider,
		ConsortiumService consortiumService) {

		super(List.of(NOT_SUPPLIED_CURRENT_SUPPLIER));

		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
		this.supplierRequestServiceProvider = supplierRequestServiceProvider;
		this.consortiumService = consortiumService;
	}

	@Override
	protected boolean checkApplicability(RequestWorkflowContext context) {
		// Always applicable because request is cancelled when re-resolution is not required
		return true;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return isReResolutionRequired(context)
			.flatMap(function(this::branchOnRequired));
	}

	private Mono<RequestWorkflowContext> branchOnRequired(
		RequestWorkflowContext context, Boolean required, String reason) {

		if (required) {
			context.getWorkflowMessages().add("ReResolution is required");
			return resolveNextSupplier(context);
		}
		else {
			return auditNotRequired(context, reason)
				.flatMap(this::cancelRequest)
				.thenReturn(context);
		}
	}

	private Mono<RequestWorkflowContext> cancelRequest(RequestWorkflowContext context) {
		log.debug("cancelRequest({})", context);

		return cancelLocalBorrowingRequest(context)
			.flatMap(this::cancelLocalPickupRequest)
			.flatMap(this::markNoItemsAvailableAtAnyAgency);
	}

	private Mono<Tuple3<RequestWorkflowContext, Boolean, String>> isReResolutionRequired(
		RequestWorkflowContext context) {

		return Mono.zip(Mono.just(context), isEnabled(), isItemManuallySelected(context))
			.map(function(ResolveNextSupplierTransition::isEnabledAndNotManuallySelectedItem));
	}

	private Mono<Boolean> isEnabled() {
		return consortiumService.findOneConsortiumFunctionalSetting(RE_RESOLUTION)
			.map(FunctionalSetting::isEnabled)
			.defaultIfEmpty(false)
			.doOnSuccess(enabled -> log.debug("Re-resolution consortium policy enabled: {}", enabled));
	}

	private static Mono<Boolean> isItemManuallySelected(RequestWorkflowContext context) {
		return Mono.just(getValue(context, RequestWorkflowContext::getPatronRequest,
			PatronRequest::getIsManuallySelectedItem, false));
	}

	private static Tuple3<RequestWorkflowContext, Boolean, String> isEnabledAndNotManuallySelectedItem(
		RequestWorkflowContext context, Boolean enabled, Boolean itemManuallySelected) {

		if (!enabled) {
			return Tuples.of(context, false, "Consortial setting is not enabled");
		}

		if  (itemManuallySelected) {
			// Do not attempt to re-resolve a request for a manually selected item
			// When a patron chooses a specific item, they also effectively choose a supplying library
			// If that library refuses to supply the item, then there are no viable alternatives
			// that re-resolution can select that would match that criteria
			return Tuples.of(context, false, "Item manually selected");
		}

		return Tuples.of(context, true, "");
	}

	// Main handler for re-resolution logic
	private Mono<RequestWorkflowContext> resolveNextSupplier(RequestWorkflowContext context) {
		log.debug("resolveNextSupplier({})", context);

		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);

		supplierRequestService = supplierRequestServiceProvider.get();

		log.info("Resolving Patron Request {}", getValue(patronRequest, PatronRequest::getId, "Unknown"));

		return findExcludedAgencyCodes(patronRequest)
			.flatMap(excludedAgencyCodes -> resolve(patronRequest, excludedAgencyCodes))
			.doOnSuccess(resolution -> log.debug("Re-resolved to: {}", resolution))
			.doOnError(error -> log.error("Error during re-resolution: {}", error.getMessage()))
			.flatMap(resolution -> applyReResolution(resolution, context))
			.thenReturn(context);
	}

	private Mono<Resolution> resolve(PatronRequest patronRequest, List<String> excludedAgencyCodes) {
		return patronRequestResolutionService.resolve(parametersFor(patronRequest, excludedAgencyCodes));
	}

	private Mono<List<String>> findExcludedAgencyCodes(PatronRequest patronRequest) {
		return supplierRequestService.findAllSupplyingAgencies(patronRequest)
			.mapNotNull(agency -> getValueOrNull(agency, DataAgency::getCode))
			.collectList()
			.doOnSuccess(codes -> log.debug("Excluded agency codes: {}", codes));
	}

	private Mono<Void> applyReResolution(Resolution resolution, RequestWorkflowContext context) {
		if (resolution.successful()) {
			return applySuccessfulResolution(resolution, context);
		}
		else {
			return cancelRequest(context)
				.then();
		}
	}

	private Mono<Void> applySuccessfulResolution(Resolution resolution,
		RequestWorkflowContext context) {

		return makeSupplierRequestInactive(resolution, context)
			.flatMap(function(this::auditResolution))
			.flatMap(function(this::checkMappedCanonicalItemType))
			.flatMap(function(this::saveSupplierRequest))
			.flatMap(function(this::updatePatronRequest))
			.then();
	}

	private Mono<Tuple2<Resolution, PatronRequest>> makeSupplierRequestInactive(
		Resolution resolution, RequestWorkflowContext context) {

		final var previousSupplierRequest = getValueOrNull(context, RequestWorkflowContext::getSupplierRequest);

		return supplierRequestService.saveInactiveSupplierRequest(previousSupplierRequest)
			.flatMap(inactiveSupplierRequest -> {
				log.info("Supplier request {} saved as inactive supplier request", inactiveSupplierRequest.getId());
				return Mono.just(resolution);
			})
			.zipWith(Mono.just(getValueOrNull(context, RequestWorkflowContext::getPatronRequest)));
	}

	private Mono<Resolution> updatePatronRequest(Resolution resolution,
		PatronRequest patronRequest) {

		log.debug("updatePatronRequest({}, {})", resolution, patronRequest);

		patronRequest.resolve();

		final var patronRequestService = patronRequestServiceProvider.get();

		return patronRequestService.updatePatronRequest(patronRequest)
			.thenReturn(resolution);
	}

	private Mono<RequestWorkflowContext> auditNotRequired(
		RequestWorkflowContext context, String reason) {

		return patronRequestAuditService.addAuditEntry(context.getPatronRequest(),
				"Re-resolution not required", Map.of("detail", reason))
			.thenReturn(context);
	}

	private Mono<Tuple2<Resolution, PatronRequest>> auditResolution(
		Resolution resolution, PatronRequest patronRequest) {

		return patronRequestResolutionService.auditResolution(resolution,
			patronRequest, "Re-resolved", patronRequestAuditService)
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<Tuple2<Resolution, PatronRequest>> checkMappedCanonicalItemType(Resolution resolution,
		PatronRequest patronRequest) {

		return Mono.just(PatronRequestResolutionService.checkMappedCanonicalItemType(resolution))
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<Tuple2<Resolution, PatronRequest>> saveSupplierRequest(Resolution resolution,
		PatronRequest patronRequest) {

		log.debug("saveSupplierRequest({}, {})", resolution, patronRequest);

		return supplierRequestService.saveSupplierRequest(
				mapToSupplierRequest(resolution.getChosenItem(), patronRequest))
			.thenReturn(resolution)
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<RequestWorkflowContext> cancelLocalBorrowingRequest(
		RequestWorkflowContext context) {

		final var borrowingHostLmsCode = getValue(context,
			RequestWorkflowContext::getPatronSystemCode, null);

		if (isEmpty(borrowingHostLmsCode)) {
			return Mono.error(new RuntimeException("Patron is not associated with a Host LMS"));
		}

		final var patronRequest = getValue(context,
			RequestWorkflowContext::getPatronRequest, null);

		final var localRequestStatus = getValue(patronRequest,
			PatronRequest::getLocalRequestStatus, "");

		if (localRequestStatus.equals(HOLD_MISSING) || localRequestStatus.equals(HOLD_CANCELLED)) {
			context.getWorkflowMessages().add("cancelLocalBorrowingRequest - HOLD_MISSING or HOLD_CANCELLED - noop");
			return Mono.just(context);
		}
		else {
			context.getWorkflowMessages().add("cancelLocalBorrowingRequest - local request status: "+localRequestStatus);
		}

		final var homePatronIdentity = getValue(context, RequestWorkflowContext::getPatronHomeIdentity, null);

		final var localRequestId = getValue(patronRequest, PatronRequest::getLocalRequestId, null);
		final var localItemId = getValue(patronRequest, PatronRequest::getLocalItemId, null);
		final var localPatronId = getValue(homePatronIdentity, PatronIdentity::getLocalId, null);

		if (isEmpty(localRequestId)) {
			final var patronRequestId = getValue(patronRequest,
				PatronRequest::getId, null);

			final var message = "Could not cancel local borrowing request because no local ID is known (ID: \"%s\")"
				.formatted(patronRequestId);

			log.warn(message);
			context.getWorkflowMessages().add(message);

			return patronRequestAuditService.addAuditEntry(patronRequest, message)
				.thenReturn(context);
		}

		context.getWorkflowMessages().add("Attemptig cancelHoldRequest "+borrowingHostLmsCode+" "+localRequestId+" "+localItemId+" "+localPatronId);
		return hostLmsService.getClientFor(borrowingHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(localRequestId)
				.localItemId(localItemId)
				.patronId(localPatronId)
				.build()))
			.thenReturn(context);
	}

	private Mono<RequestWorkflowContext> cancelLocalPickupRequest(
		RequestWorkflowContext context) {

		final var patronRequest = getValue(context,
			RequestWorkflowContext::getPatronRequest, null);

		// No need to cancel a pickup request if the active workflow is not RET-PUA
		if (!"RET-PUA".equals(patronRequest.getActiveWorkflow())) {
			return Mono.just(context);
		}

		final var pickupHostLmsCode = getValue(context,
			RequestWorkflowContext::getPickupSystemCode, null);

		if (isEmpty(pickupHostLmsCode)) {
			return Mono.error(new RuntimeException("Pickup hostlms code not found"));
		}

		final var pickupRequestStatus = getValue(patronRequest,
			PatronRequest::getPickupRequestStatus, "");

		if (pickupRequestStatus.equals(HOLD_MISSING) || pickupRequestStatus.equals(HOLD_CANCELLED)) {
			log.info("Pickup request doesn't require cancellation");

			return Mono.just(context);
		}

		final var pickupPatronIdentity = getValue(context, RequestWorkflowContext::getPickupPatronIdentity, null);

		final var pickupRequestId = getValue(patronRequest, PatronRequest::getPickupRequestId, null);
		final var pickupItemId = getValue(patronRequest, PatronRequest::getPickupItemId, null);
		final var pickupPatronId = getValue(pickupPatronIdentity, PatronIdentity::getLocalId, null);

		if (isEmpty(pickupRequestId)) {
			final var patronRequestId = getValue(patronRequest,
				PatronRequest::getId, null);

			final var message = "Could not cancel pickup request because no local ID is known (PR ID: \"%s\")"
				.formatted(patronRequestId);

			log.warn(message);

			return patronRequestAuditService.addAuditEntry(patronRequest, message)
				.thenReturn(context);
		}

		return hostLmsService.getClientFor(pickupHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(pickupRequestId)
				.localItemId(pickupItemId)
				.patronId(pickupPatronId)
				.build()))
			.thenReturn(context);
	}

	private Mono<RequestWorkflowContext> markNoItemsAvailableAtAnyAgency(RequestWorkflowContext context) {
		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);

		return Mono.just(context.setPatronRequest(
			patronRequest.setStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY)));
	}

	@Override
	public Optional<PatronRequest.Status> getTargetStatus() {
		return Optional.of(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY);
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
		return List.of(new DCBTransitionResult("NO_ITEMS_SELECTABLE_AT_ANY_AGENCY", NO_ITEMS_SELECTABLE_AT_ANY_AGENCY.toString()));
	}
}
