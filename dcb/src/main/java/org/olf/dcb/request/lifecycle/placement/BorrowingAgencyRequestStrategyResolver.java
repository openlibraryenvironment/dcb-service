package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleOperation;

@Prototype
public class BorrowingAgencyRequestStrategyResolver {
	private final ImperativeBorrowingAgencyRequestStrategy imperativeStrategy;

	public BorrowingAgencyRequestStrategyResolver(
		ImperativeBorrowingAgencyRequestStrategy imperativeStrategy) {

		this.imperativeStrategy = imperativeStrategy;
	}

	public BorrowingAgencyRequestStrategy resolve(
		RequestWorkflowContext context,
		LifecycleOperation operation) {

		return imperativeStrategy;
	}
}
