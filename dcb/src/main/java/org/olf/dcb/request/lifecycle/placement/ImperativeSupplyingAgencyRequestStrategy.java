package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.StrategyType;
import reactor.core.publisher.Mono;

@Prototype
public class ImperativeSupplyingAgencyRequestStrategy
	implements SupplyingAgencyRequestStrategy {

	private final SupplyingAgencyService supplyingAgencyService;

	public ImperativeSupplyingAgencyRequestStrategy(
		SupplyingAgencyService supplyingAgencyService) {

		this.supplyingAgencyService = supplyingAgencyService;
	}

	@Override
	public StrategyType type() {
		return StrategyType.IMPERATIVE;
	}

	@Override
	public Mono<SupplyingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return supplyingAgencyService
			.placePatronRequestAtSupplyingAgency(context.getPatronRequest())
			.map(patronRequest -> SupplyingAgencyRequestResult.from(
				patronRequest, context.getSupplierRequest()));
	}
}
