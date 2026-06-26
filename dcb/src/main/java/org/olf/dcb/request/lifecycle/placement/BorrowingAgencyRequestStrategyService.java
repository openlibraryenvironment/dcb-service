package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import reactor.core.publisher.Mono;

@Prototype
public class BorrowingAgencyRequestStrategyService {
	private final BorrowingAgencyRequestStrategyResolver strategyResolver;
	private final BorrowingAgencyRequestProjector projector;

	public BorrowingAgencyRequestStrategyService(
		BorrowingAgencyRequestStrategyResolver strategyResolver,
		BorrowingAgencyRequestProjector projector) {

		this.strategyResolver = strategyResolver;
		this.projector = projector;
	}

	public Mono<RequestWorkflowContext> place(RequestWorkflowContext context) {
		final var strategy = strategyResolver.resolve(
			context, LifecycleOperation.PLACE_REQUEST);

		return strategy.place(context)
			.map(result -> projector.apply(context, result));
	}

	public Mono<RequestWorkflowContext> revise(RequestWorkflowContext context) {
		final var strategy = strategyResolver.resolve(
			context, LifecycleOperation.REVISE_REQUEST);

		return strategy.revise(context)
			.map(result -> projector.apply(context, result));
	}
}
