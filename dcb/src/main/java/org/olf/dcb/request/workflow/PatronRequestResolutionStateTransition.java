package org.olf.dcb.request.workflow;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.dcb.request.resolution.ResolutionParameters.parametersFor;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.Item;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

		return resolve(patronRequest)
			// Trail switching these so we can set current supplier request on patron request
			.flatMap(function(this::auditResolution))
			.flatMap(function(this::checkMappedCanonicalItemType))
			.flatMap(function(this::saveSupplierRequest))
			.flatMap(function(this::setPatronRequestWorkflow))
			.flatMap(function(this::updatePatronRequest))
			// This is the original context, rather than the context created in setPatronRequestWorkflow
			// That could mean the information returned here is incorrect
			.thenReturn(ctx);
	}

	private Mono<Tuple2<Resolution, PatronRequest>> resolve(PatronRequest patronRequest) {
		return patronRequestResolutionService.resolve(parametersFor(patronRequest, emptyList()))
			.doOnSuccess(resolution -> log.debug("Resolved to: {}", resolution))
			.doOnError(error -> log.error("Error occurred during resolution: {}", error.getMessage()))
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<Tuple2<Resolution, PatronRequest>> setPatronRequestWorkflow(
		Resolution resolution, PatronRequest patronRequest) {

		final var borrowingAgencyCode = getValueOrNull(resolution, Resolution::getBorrowingAgencyCode);
		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);
		final var itemAgencyCode = getValueOrNull(chosenItem, Item::getAgencyCode);

		// NO_ITEMS_SELECTABLE_AT_ANY_AGENCY
		if (chosenItem == null) {
			return Mono.just(resolution)
				.zipWith(Mono.just(patronRequest));
		}

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
			.map(p -> Tuples.of(resolution, p));
	}

	private Mono<Tuple2<Resolution, PatronRequest>> auditResolution(
		Resolution resolution, PatronRequest patronRequest) {

		return patronRequestResolutionService.auditResolution(resolution,
				patronRequest, "Resolution")
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<Tuple2<Resolution, PatronRequest>> checkMappedCanonicalItemType(Resolution resolution,
		PatronRequest patronRequest) {

		return Mono.just(PatronRequestResolutionService.checkMappedCanonicalItemType(resolution))
			.zipWith(Mono.just(patronRequest));
	}

	private Mono<Void> updatePatronRequest(Resolution resolution, PatronRequest patronRequest) {
		log.debug("updatePatronRequest({}, {})", resolution, patronRequest);

		if (resolution.getChosenItem() != null) {
			patronRequest.resolve();
		} else {
			patronRequest.resolveToNoItemsSelectable();
		}

		final var patronRequestService = patronRequestServiceProvider.get();

		return patronRequestService.updatePatronRequest(patronRequest)
			.then();
	}

	private Mono<Tuple2<Resolution, PatronRequest>> saveSupplierRequest(Resolution resolution,
		PatronRequest patronRequest) {

		log.debug("saveSupplierRequest({}, {})", resolution, patronRequest);

		final var chosenItem = resolution.getChosenItem();

		if (chosenItem == null) {
			return Mono.just(resolution)
				.zipWith(Mono.just(patronRequest));
		}

		return supplierRequestService.saveSupplierRequest(
				mapToSupplierRequest(chosenItem, patronRequest))
			.thenReturn(resolution)
			.zipWith(Mono.just(patronRequest));
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
