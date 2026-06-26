package org.olf.dcb.request.lifecycle.placement;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.StrategyType;
import reactor.core.publisher.Mono;

@Prototype
public class ImperativeBorrowingAgencyRequestStrategy
	implements BorrowingAgencyRequestStrategy {

	private final BorrowingAgencyService borrowingAgencyService;

	public ImperativeBorrowingAgencyRequestStrategy(
		BorrowingAgencyService borrowingAgencyService) {

		this.borrowingAgencyService = borrowingAgencyService;
	}

	@Override
	public StrategyType type() {
		return StrategyType.IMPERATIVE;
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return borrowingAgencyService.placePatronRequestAtBorrowingAgency(context)
			.map(BorrowingAgencyRequestResult::from);
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> revise(
		RequestWorkflowContext context) {

		return borrowingAgencyService.updatePatronRequestAtBorrowingAgency(context)
			.map(BorrowingAgencyRequestResult::from);
	}
}
