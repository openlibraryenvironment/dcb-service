package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.StrategyType;
import reactor.core.publisher.Mono;

public interface BorrowingAgencyRequestStrategy {
	StrategyType type();

	default boolean supports(RequestWorkflowContext context) {
		return true;
	}

	default boolean supportsProtocol(String protocol) {
		return true;
	}

	Mono<BorrowingAgencyRequestResult> place(RequestWorkflowContext context);

	Mono<BorrowingAgencyRequestResult> revise(RequestWorkflowContext context);
}
