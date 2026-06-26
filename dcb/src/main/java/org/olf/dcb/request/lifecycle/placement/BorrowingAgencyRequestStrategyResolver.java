package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityConfigurationException;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;

@Prototype
public class BorrowingAgencyRequestStrategyResolver {
	private final ImperativeBorrowingAgencyRequestStrategy imperativeStrategy;
	private final LifecycleCapabilityResolver capabilityResolver;

	public BorrowingAgencyRequestStrategyResolver(
		ImperativeBorrowingAgencyRequestStrategy imperativeStrategy,
		LifecycleCapabilityResolver capabilityResolver) {

		this.imperativeStrategy = imperativeStrategy;
		this.capabilityResolver = capabilityResolver;
	}

	public BorrowingAgencyRequestStrategy resolve(
		RequestWorkflowContext context,
		LifecycleOperation operation) {

		final var strategy = capabilityResolver.placementStrategy(
			LifecycleRole.BORROWER);

		if (strategy == StrategyType.IMPERATIVE) {
			return imperativeStrategy;
		}

		throw new LifecycleCapabilityConfigurationException(
			"Borrowing agency declarative request strategy is not available");
	}
}
