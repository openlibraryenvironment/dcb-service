package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SupplyingAgencyRequestStrategyService {
	private final SupplyingAgencyRequestStrategyResolver strategyResolver;
	private final SupplyingAgencyRequestProjector projector;

	public SupplyingAgencyRequestStrategyService(
		SupplyingAgencyRequestStrategyResolver strategyResolver,
		SupplyingAgencyRequestProjector projector) {

		this.strategyResolver = strategyResolver;
		this.projector = projector;
	}

	public Mono<RequestWorkflowContext> place(RequestWorkflowContext context) {
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
			.map(result -> projector.apply(context, result));
	}
}
