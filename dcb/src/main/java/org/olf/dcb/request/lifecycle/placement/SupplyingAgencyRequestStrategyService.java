package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
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

	public SupplyingAgencyRequestStrategyService(
		SupplyingAgencyRequestStrategyResolver strategyResolver,
		SupplyingAgencyRequestProjector projector,
		SupplierRequestRepository supplierRequestRepository,
		SupplierPlacementGate supplierPlacementGate) {

		this.strategyResolver = strategyResolver;
		this.projector = projector;
		this.supplierRequestRepository = supplierRequestRepository;
		this.supplierPlacementGate = supplierPlacementGate;
	}

	// The gate runs ahead of every strategy, and a refusal never reaches the projector, which records a placement
	public Mono<RequestWorkflowContext> place(RequestWorkflowContext context) {
		return supplierPlacementGate.check(context)
			.flatMap(preflight -> preflight.cannotSupply()
				? supplierPlacementGate.refuse(context, preflight)
				: supplierPlacementGate.warnIfUnreachable(context, preflight).flatMap(this::placeWithStrategy));
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
