package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.StrategyType;
import reactor.core.publisher.Mono;

public interface SupplyingAgencyRequestStrategy {
	StrategyType type();

	default boolean supports(RequestWorkflowContext context) {
		return true;
	}

	default boolean supportsProtocol(String protocol) {
		return true;
	}

	Mono<SupplyingAgencyRequestResult> place(RequestWorkflowContext context);
}
