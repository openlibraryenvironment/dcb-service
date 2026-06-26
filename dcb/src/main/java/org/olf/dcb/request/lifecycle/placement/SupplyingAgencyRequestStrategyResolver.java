package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityConfigurationException;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;

@Prototype
public class SupplyingAgencyRequestStrategyResolver {
	private final ImperativeSupplyingAgencyRequestStrategy imperativeStrategy;
	private final LifecycleCapabilityResolver capabilityResolver;

	public SupplyingAgencyRequestStrategyResolver(
		ImperativeSupplyingAgencyRequestStrategy imperativeStrategy,
		LifecycleCapabilityResolver capabilityResolver) {

		this.imperativeStrategy = imperativeStrategy;
		this.capabilityResolver = capabilityResolver;
	}

	public SupplyingAgencyRequestStrategy resolve(
		RequestWorkflowContext context,
		LifecycleOperation operation) {

		final var strategy = capabilityResolver.placementStrategy(
			LifecycleRole.SUPPLIER);

		if (strategy == StrategyType.IMPERATIVE) {
			return imperativeStrategy;
		}

		throw new LifecycleCapabilityConfigurationException(
			"Supplying agency declarative request strategy is not available");
	}
}
