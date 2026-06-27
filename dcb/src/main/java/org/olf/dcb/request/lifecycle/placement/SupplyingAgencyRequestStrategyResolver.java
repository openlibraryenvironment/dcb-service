package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import java.util.List;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityConfigurationException;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;

@Prototype
public class SupplyingAgencyRequestStrategyResolver {
	private final ImperativeSupplyingAgencyRequestStrategy imperativeStrategy;
	private final List<SupplyingAgencyRequestStrategy> declarativeStrategies;
	private final LifecycleCapabilityResolver capabilityResolver;

	public SupplyingAgencyRequestStrategyResolver(
		ImperativeSupplyingAgencyRequestStrategy imperativeStrategy,
		List<SupplyingAgencyRequestStrategy> strategies,
		LifecycleCapabilityResolver capabilityResolver) {

		this.imperativeStrategy = imperativeStrategy;
		this.declarativeStrategies = strategies.stream()
			.filter(strategy -> strategy.type() == StrategyType.DECLARATIVE)
			.toList();
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

		return declarativeStrategy(
			capabilityResolver.placementProtocol(LifecycleRole.SUPPLIER),
			context);
	}

	private SupplyingAgencyRequestStrategy declarativeStrategy(
		String protocol,
		RequestWorkflowContext context) {

		final var matchingStrategies = declarativeStrategies.stream()
			.filter(strategy -> strategy.supportsProtocol(protocol))
			.filter(strategy -> strategy.supports(context))
			.toList();

		if (matchingStrategies.size() == 1) {
			return matchingStrategies.getFirst();
		}

		if (matchingStrategies.isEmpty()) {
			throw new LifecycleCapabilityConfigurationException(
				"Supplying agency declarative request strategy is not available for protocol "
					+ protocol);
		}

		throw new LifecycleCapabilityConfigurationException(
			"Multiple supplying agency declarative request strategies are available for protocol "
				+ protocol);
	}
}
