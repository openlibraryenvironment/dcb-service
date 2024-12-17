package org.olf.dcb.request.workflow;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.request.resolution.SupplierRequestService.mapToSupplierRequest;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.MapUtils.putNonNullValue;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
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

	ResolveNextSupplierTransition(HostLmsService hostLmsService,
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
		return true;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return isReResolutionRequired(context)
			.flatMap(isRequired -> {
				log.info("Re-resolution required: {}", isRequired);

				if (isRequired) {
					return resolveNextSupplier(context);
				}
				else {
					return terminateSupplierCancellation(context);
				}
			});
	}

	private Mono<RequestWorkflowContext> terminateSupplierCancellation(RequestWorkflowContext context) {
		log.warn("terminateSupplierCancellation");

		return cancelLocalBorrowingRequest(context)
			.flatMap(this::markNoItemsAvailableAtAnyAgency);
	}

	// Method to check if re-resolution conditions are met
	private Mono<Boolean> isReResolutionRequired(RequestWorkflowContext context) {
		return Mono.zip(
			getConsortiumReResolutionPolicy(context),
			getSupportedLmsForReResolution(context),
			(isConsortiumSupported, isLmsSupported) -> isConsortiumSupported && isLmsSupported
		);
	}

	private Mono<Boolean> getSupportedLmsForReResolution(RequestWorkflowContext context) {

		final var patronSystemCode = Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(PatronRequest::getPatronHostlmsCode)
			.orElse(null);

		if (patronSystemCode == null) {
			log.warn("Patron system code is null");

			return Mono.just(false)
				.doOnSuccess(supported -> log.debug("Re-resolution LMS supported: {}", supported));
		}

		return hostLmsService.getClientFor(patronSystemCode)
			.flatMap(HostLmsClient::isReResolutionSupported)
			.map(isSupported -> isSupported != null && isSupported)
			.doOnSuccess(supported -> log.debug("Re-resolution LMS supported: {}", supported));
	}

	private Mono<Boolean> getConsortiumReResolutionPolicy(RequestWorkflowContext context) {
		return consortiumService.findOneConsortiumFunctionalSetting(FunctionalSettingType.RE_RESOLUTION)
			.map(FunctionalSetting::isEnabled)
			.defaultIfEmpty(false)
			.doOnSuccess(enabled -> log.debug("Re-resolution consortium policy enabled: {}", enabled));
	}

	// Main handler for re-resolution logic
	private Mono<RequestWorkflowContext> resolveNextSupplier(RequestWorkflowContext context) {
		log.debug("resolveNextSupplier");

		final var patronRequest = getValue(context, RequestWorkflowContext::getPatronRequest, null);
		supplierRequestService = supplierRequestServiceProvider.get();

		return checkAndIncludeCurrentSupplierRequestsFor(patronRequest)
			.flatMap(this::resolve)
			.flatMap(resolution -> transitionSupplierRequest(resolution, context))
			.doOnSuccess(resolution -> log.debug("Re-resolved to: {}", resolution))
			.doOnError(error -> log.error("Error during re-resolution: {}", error.getMessage()))
			.flatMap(this::auditResolution)
			.map(PatronRequestResolutionService::checkMappedCanonicalItemType)
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::updatePatronRequest)
			.thenReturn(context);
	}

	private Mono<Resolution> resolve(PatronRequest patronRequest) {
		log.info("Re-resolving Patron Request {}", getValue(patronRequest,
			PatronRequest::getId, "Unknown"));

		final var excludedAgencyCode = getValue(patronRequest,
			PatronRequest::determineSupplyingAgencyCode, null);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest,
			excludedAgencyCode);
	}

	/**
	 * Transitions the previous supplier request to an inactive state.
	 *
	 * This method takes the current resolution and the request workflow context as input,
	 * saves the previous supplier request as an inactive supplier request, and returns the resolution.
	 *
	 * @param resolution the current resolution
	 * @param context the request workflow context
	 * @return a Mono containing the resolution
	 */
	private Mono<Resolution> transitionSupplierRequest(Resolution resolution, RequestWorkflowContext context) {

		final var previousSupplierRequest = getValueOrNull(context, RequestWorkflowContext::getSupplierRequest);

		return supplierRequestService.saveInactiveSupplierRequest(previousSupplierRequest)
			.flatMap(inactiveSupplierRequest -> {
				log.info("Supplier request {} saved as inactive supplier request", inactiveSupplierRequest.getId());
				return Mono.just(resolution);
			});
	}

	private Mono<PatronRequest> checkAndIncludeCurrentSupplierRequestsFor(PatronRequest patronRequest) {
		return supplierRequestService.findAllSupplierRequestsWithDataAgencyFor(patronRequest)
			.map(patronRequest::setSupplierRequests)
			.doOnSuccess(pr -> log.info("Supplier request and data agency successfully included for patron request {}", pr.getId()))
			.doOnError(error -> log.error("Error including supplier request and data agency for patron request {}", patronRequest.getId(), error));
	}

	private Mono<Resolution> updatePatronRequest(Resolution resolution) {
		log.debug("updatePatronRequest({})", resolution);

		final var patronRequest = resolution.getPatronRequest();

		if (resolution.getChosenItem() != null) {
			patronRequest.resolve();
		} else {
			patronRequest.resolveToNoItemsSelectable();
		}

		final var patronRequestService = patronRequestServiceProvider.get();

		return patronRequestService.updatePatronRequest(patronRequest)
			.thenReturn(resolution);
	}

	private Mono<Resolution> auditResolution(Resolution resolution) {
		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		// Do not audit a resolution when an item hasn't been chosen
		if (chosenItem == null) {
			return Mono.just(resolution);
		}

		final var auditData = new HashMap<String, Object>();

		final var itemStatusCode = getValueOrNull(chosenItem, Item::getStatus, ItemStatus::getCode);

		// For values that could be "unknown", "null" is used as a differentiating default
		final var presentableItem = PatronRequestResolutionStateTransition.PresentableItem.builder()
			.barcode(getValue(chosenItem, Item::getBarcode, "Unknown"))
			.statusCode(getValue(itemStatusCode, Enum::name, "null"))
			.requestable(getValue(chosenItem, Item::getIsRequestable, false))
			.localItemType(getValue(chosenItem, Item::getLocalItemType, "null"))
			.canonicalItemType(getValue(chosenItem, Item::getCanonicalItemType, "null"))
			.holdCount(getValue(chosenItem, Item::getHoldCount, 0))
			.agencyCode(getValue(chosenItem, Item::getAgencyCode, "Unknown"))
			.build();

		putNonNullValue(auditData, "selectedItem", presentableItem);

		return patronRequestAuditService.addAuditEntry(resolution.getPatronRequest(),
				"Re-resolved to item with local ID \"%s\" from Host LMS \"%s\"".formatted(
					chosenItem.getLocalId(), chosenItem.getHostLmsCode()), auditData)
			.then(Mono.just(resolution));
	}

	private Mono<Resolution> saveSupplierRequest(Resolution resolution) {
		log.debug("saveSupplierRequest({})", resolution);

		final var chosenItem = resolution.getChosenItem();

		if (chosenItem == null) {
			return Mono.just(resolution);
		}

		return supplierRequestService.saveSupplierRequest(
				mapToSupplierRequest(chosenItem, resolution.getPatronRequest()))
			.thenReturn(resolution);
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
			return Mono.just(context);
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

			return patronRequestAuditService.addAuditEntry(patronRequest, message)
				.thenReturn(context);
		}

		return hostLmsService.getClientFor(borrowingHostLmsCode)
			.flatMap(hostLmsClient -> hostLmsClient.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(localRequestId)
				.localItemId(localItemId)
				.patronId(localPatronId)
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
