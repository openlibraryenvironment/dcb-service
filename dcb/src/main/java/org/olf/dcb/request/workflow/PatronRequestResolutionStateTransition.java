package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.MapUtils.putNonNullValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.SupplierRequestService;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestAuditService patronRequestAuditService;

	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;
	private final SupplierRequestService supplierRequestService;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;

	private static final List<Status> possibleSourceStatus = List.of(PATRON_VERIFIED);

	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		PatronRequestAuditService patronRequestAuditService,
		BeanProvider<PatronRequestService> patronRequestServiceProvider,
		SupplierRequestService supplierRequestService,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
		this.supplierRequestService = supplierRequestService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
	}

	// Right now we assume that this is always the first supplier we are talking to.. In the future we need to
	// be able to handle a supplier failing to deliver and creating a new request for a different supplier.
	// isActive is intended to identify the "Current" supplier as we try different agencies.
	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();

		log.info("PatronRequestResolutionStateTransition attempt for {}", patronRequest);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest)
			.doOnSuccess(resolution -> log.debug("Resolved to: {}", resolution))
			.doOnError(error -> log.error("Error occurred during resolution: {}", error.getMessage()))
			// Trail switching these so we can set current supplier request on patron request
			.flatMap(this::auditResolution)
			.map(PatronRequestResolutionService::checkMappedCanonicalItemType)
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::setPatronRequestWorkflow)
			.flatMap(this::updatePatronRequest)
			.map(Resolution::getPatronRequest)
			.thenReturn(ctx);
	}

	private Mono<Resolution> setPatronRequestWorkflow(Resolution resolution) {
		final var patronRequest = getValueOrNull(resolution, Resolution::getPatronRequest);
		final var borrowingAgencyCode = getValueOrNull(resolution, Resolution::getBorrowingAgencyCode);
		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);
		final var itemAgencyCode = getValueOrNull(chosenItem, Item::getAgencyCode);

		// NO_ITEMS_SELECTABLE_AT_ANY_AGENCY
		if (chosenItem == null) return Mono.just(resolution);

		log.debug("Setting PatronRequestWorkflow BorrowingAgencyCode: {}, ItemAgencyCode: {}",
			borrowingAgencyCode, itemAgencyCode);

		// build a temporary context to allow the active workflow to be set
		final var rwc = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setPatronAgencyCode(borrowingAgencyCode)
			.setLenderAgencyCode(itemAgencyCode);

		return requestWorkflowContextHelper.resolvePickupLocationAgency(rwc)
			.flatMap(requestWorkflowContextHelper::setPatronRequestWorkflow)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(resolution::withPatronRequest);
	}

	private Mono<Resolution> auditResolution(Resolution resolution) {
		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		// Do not audit a resolution when an item hasn't been chosen
		if (chosenItem == null) {
			return Mono.just(resolution);
		}

		final var auditData = new HashMap<String, Object>();

		// For values that could be "unknown", "null" is used as a differentiating default
		final var presentableItem = buildPresentableItem(chosenItem);

		putNonNullValue(auditData, "selectedItem", presentableItem);

		// adding the list the item was chosen from, helps us debug the resolution
		final var sortedItems = resolution.getSortedItems().stream()
			.map(this::buildPresentableItem).collect(Collectors.toList());

		putNonNullValue(auditData, "sortedItems", sortedItems);

		return patronRequestAuditService.addAuditEntry(resolution.getPatronRequest(),
				"Resolved to item with local ID \"%s\" from Host LMS \"%s\"".formatted(
					chosenItem.getLocalId(), chosenItem.getHostLmsCode()), auditData)
			.then(Mono.just(resolution));
	}

	private PresentableItem buildPresentableItem(Item item) {
		return PresentableItem.builder()
			.localId(getValue(item, Item::getLocalId, "Unknown"))
			.barcode(getValue(item, Item::getBarcode, "Unknown"))
			.statusCode(getStatusCode(item))
			.requestable(getValue(item, Item::getIsRequestable, false))
			.localItemType(getValue(item, Item::getLocalItemType, "null"))
			.canonicalItemType(getValue(item, Item::getCanonicalItemType, "null"))
			.holdCount(getValue(item, Item::getHoldCount, 0))
			.agencyCode(getValue(item, Item::getAgencyCode, "Unknown"))
			.availableDate(Optional.ofNullable(getValue(item, Item::getAvailableDate, null))
				.map(Instant::toString).orElse("null"))
			.dueDate(Optional.ofNullable(getValue(item, Item::getDueDate, null))
				.map(Instant::toString).orElse("null"))
			.build();
	}

	private String getStatusCode(Item chosenItem) {
		final var itemStatusCode = getValueOrNull(chosenItem, Item::getStatus, ItemStatus::getCode);
		return getValue(itemStatusCode, Enum::name, "null");
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

	private static SupplierRequest mapToSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("mapToSupplierRequest({}, {})", item, patronRequest);

		final var supplierRequestId = randomUUID();

		log.debug("create SupplierRequest: {}, {}, {}", supplierRequestId, item, item.getHostLmsCode());

		return SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(patronRequest)
			.localItemId(item.getLocalId())
			.localBibId(item.getLocalBibId())
			.localItemBarcode(item.getBarcode())
			.localItemLocationCode(item.getLocation().getCode())
			.localItemType(item.getLocalItemType())
			.canonicalItemType(item.getCanonicalItemType())
			.hostLmsCode(item.getHostLmsCode())
			.localAgency(item.getAgencyCode())
			.statusCode(PENDING)
			.isActive(true)
			.resolvedAgency(item.getAgency())
			.build();
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	// getTargetStatus tells us where we're trying to get to BUT be aware that the transitions can have error states so the Status
	// outcome can be OTHER than the status listed here. This is used for goal-seeking when trying to work out which transitions to
	// apply - it's not a statement of what the status WILL be after applying the transition. n this case - NO_ITEMS_AT_ANY_SUPPLIER can
	// happen
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(RESOLVED);
	}

  @Override
  public String getName() {
    return "PatronRequestResolutionStateTransition";
  }

  @Override
  public boolean attemptAutomatically() {
    return true;
  }
}
