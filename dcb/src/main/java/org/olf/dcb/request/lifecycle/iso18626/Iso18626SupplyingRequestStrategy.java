package org.olf.dcb.request.lifecycle.iso18626;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestResult;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestStrategy;
import reactor.core.publisher.Mono;

public class Iso18626SupplyingRequestStrategy
	implements SupplyingAgencyRequestStrategy {

	@Override
	public StrategyType type() {
		return StrategyType.DECLARATIVE;
	}

	@Override
	public Mono<SupplyingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return Mono.error(new UnsupportedOperationException(
			"ISO18626 supplying request placement is not activated yet"));
	}
}
