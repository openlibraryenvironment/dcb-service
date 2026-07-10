package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.resolution.SupplierRequestService;
import reactor.core.publisher.Mono;

@Prototype
public class ImperativeSupplyingAgencyRequestStrategy
	implements SupplyingAgencyRequestStrategy {

	private final SupplyingAgencyService supplyingAgencyService;
	private final SupplierRequestService supplierRequestService;

	public ImperativeSupplyingAgencyRequestStrategy(
		SupplyingAgencyService supplyingAgencyService,
		SupplierRequestService supplierRequestService) {

		this.supplyingAgencyService = supplyingAgencyService;
		this.supplierRequestService = supplierRequestService;
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
			.flatMap(patronRequest -> supplierRequestService
				.findActiveSupplierRequestFor(patronRequest)
				.map(supplierRequest -> SupplyingAgencyRequestResult.from(
					patronRequest, supplierRequest)));
	}
}
