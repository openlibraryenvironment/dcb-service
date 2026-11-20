package org.olf.dcb.request.lifecycle.placement;

import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLUTION;
import static org.olf.dcb.core.model.FunctionalSettingType.RE_RESOLVE_SUPPLIER_REFUSALS;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplierPlacementGate;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.storage.SupplierRequestRepository;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SupplyingAgencyRequestStrategyService {
	private final SupplyingAgencyRequestStrategyResolver strategyResolver;
	private final SupplyingAgencyRequestProjector projector;
	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplierPlacementGate supplierPlacementGate;
	private final ConsortiumService consortiumService;

	public SupplyingAgencyRequestStrategyService(
		SupplyingAgencyRequestStrategyResolver strategyResolver,
		SupplyingAgencyRequestProjector projector,
		SupplierRequestRepository supplierRequestRepository,
		SupplierPlacementGate supplierPlacementGate,
		ConsortiumService consortiumService) {

		this.strategyResolver = strategyResolver;
		this.projector = projector;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplierPlacementGate = supplierPlacementGate;
		this.consortiumService = consortiumService;
	}

	// The gate runs ahead of every strategy, and a refusal never reaches the projector, which records a placement
	public Mono<RequestWorkflowContext> place(RequestWorkflowContext context) {
		return supplierPlacementGate.check(context)
			.flatMap(preflight -> preflight.cannotSupply()
				? supplierPlacementGate.refuse(context, preflight)
				: supplierPlacementGate.warnIfUnreachable(context, preflight).flatMap(this::placeWithStrategy))
			// A refusal that happened, not a preflight guess, so DCB_REQUESTS_SUPPLIER_PREFLIGHT_ENABLED does not apply
			.onErrorResume(SupplierRefusedRequestProblem.class, refusal -> refusalEndsInError(context)
				.flatMap(endsInError -> endsInError
					? Mono.<RequestWorkflowContext>error(refusal)
					: supplierPlacementGate.refused(context, cannotSupply(refusal))));
	}

	// A copy staff chose is never swapped for another. Otherwise the consortium decides, and without
	// its decision a refusal ends in ERROR, as it did before the setting existed. Without RE_RESOLUTION
	// nothing would look for another supplier, and the request would end with no valid item although
	// it has one that a configuration change at the supplier would let it have
	private Mono<Boolean> refusalEndsInError(RequestWorkflowContext context) {
		return isChosenCopy(context)
			? Mono.just(true)
			: Mono.zip(consortiumService.isEnabled(RE_RESOLVE_SUPPLIER_REFUSALS), consortiumService.isEnabled(RE_RESOLUTION))
				.map(enabled -> !(enabled.getT1() && enabled.getT2()));
	}

	private static boolean isChosenCopy(RequestWorkflowContext context) {
		return Boolean.TRUE.equals(getValueOrNull(context, RequestWorkflowContext::getPatronRequest,
			PatronRequest::getIsManuallySelectedItem));
	}

	private static SupplierPreflight cannotSupply(SupplierRefusedRequestProblem refusal) {
		return SupplierPreflight.cannotSupply(refusal.getReason(),
			"Only %s's staff can change this, in %s's own system".formatted(refusal.getHostLmsCode(), refusal.getHostLmsCode()));
	}

	private Mono<RequestWorkflowContext> placeWithStrategy(RequestWorkflowContext context) {
		final var strategy = strategyResolver.resolve(
			context, LifecycleOperation.PLACE_REQUEST);
		final var patronRequest = context.getPatronRequest();
		final var supplierRequest = context.getSupplierRequest();

		log.info("DCB-LIFECYCLE-SUPPLIER-PLACEMENT: strategy={} patronRequest={} supplierRequest={} hostLms={}",
			strategy.type(),
			patronRequest != null ? patronRequest.getId() : null,
			supplierRequest != null ? supplierRequest.getId() : null,
			supplierRequest != null ? supplierRequest.getHostLmsCode() : null);

		return strategy.place(context)
			.switchIfEmpty(Mono.error(new IllegalStateException(
				"DCB-LIFECYCLE-SUPPLIER-PLACEMENT-EMPTY: supplier placement completed without a result for patronRequest=%s supplierRequest=%s strategy=%s"
					.formatted(
						patronRequest != null ? patronRequest.getId() : null,
						supplierRequest != null ? supplierRequest.getId() : null,
						strategy.type()))))
			.map(result -> projector.apply(context, result))
			.flatMap(this::persistSupplierRequest);
	}

	private Mono<RequestWorkflowContext> persistSupplierRequest(
		RequestWorkflowContext context) {

		if (context.getSupplierRequest() == null) {
			return Mono.just(context);
		}

		return Mono.from(supplierRequestRepository.saveOrUpdate(
				context.getSupplierRequest()))
			.map(saved -> {
				context.setSupplierRequest(saved);
				return context;
			});
	}
}
