package org.olf.dcb.request.lifecycle.iso18626;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestResult;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestStrategy;
import reactor.core.publisher.Mono;

public class Iso18626BorrowingRequestStrategy
	implements BorrowingAgencyRequestStrategy {

	@Override
	public StrategyType type() {
		return StrategyType.DECLARATIVE;
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return Mono.error(new UnsupportedOperationException(
			"ISO18626 borrowing request placement is not activated yet"));
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> revise(
		RequestWorkflowContext context) {

		return Mono.error(new UnsupportedOperationException(
			"ISO18626 borrowing request revision is not activated yet"));
	}
}
