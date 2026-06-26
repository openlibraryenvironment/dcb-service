package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;

@Prototype
public class SupplyingAgencyRequestStrategyResolver {
	private final ImperativeSupplyingAgencyRequestStrategy imperativeStrategy;

	public SupplyingAgencyRequestStrategyResolver(
		ImperativeSupplyingAgencyRequestStrategy imperativeStrategy) {

		this.imperativeStrategy = imperativeStrategy;
	}

	public SupplyingAgencyRequestStrategy resolve(
		RequestWorkflowContext context,
		LifecycleOperation operation) {

		return imperativeStrategy;
	}
}
