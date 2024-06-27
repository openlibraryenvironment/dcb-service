package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.MapUtils.putNonNullValue;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.utils.PropertyAccessUtils;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestAuditService patronRequestAuditService;
	
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;
	private final SupplierRequestService supplierRequestService;

	private static final List<Status> possibleSourceStatus = List.of(PATRON_VERIFIED);

	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		PatronRequestAuditService patronRequestAuditService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		BeanProvider<PatronRequestService> patronRequestServiceProvider,
		SupplierRequestService supplierRequestService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
		this.supplierRequestService = supplierRequestService;
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
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::updatePatronRequest)
			.map(Resolution::getPatronRequest)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			.thenReturn(ctx);
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
		final var presentableItem = PresentableItem.builder()
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
				"Resolved to item with local ID \"%s\" from Host LMS \"%s\"".formatted(
					chosenItem.getLocalId(), chosenItem.getHostLmsCode()), auditData)
			.then(Mono.just(resolution));
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

	@Serdeable
	@Value
	@Builder
	public static class PresentableItem {
		String barcode;
		String statusCode;
		Boolean requestable;
		String localItemType;
		String canonicalItemType;
		Integer holdCount;
		String agencyCode;
	}
}
