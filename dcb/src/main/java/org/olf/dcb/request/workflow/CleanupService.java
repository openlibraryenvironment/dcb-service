package org.olf.dcb.request.workflow;

import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@Singleton
@AllArgsConstructor
public class CleanupService {
	private final SupplyingAgencyService supplyingAgencyService;
	private final BorrowingAgencyService borrowingAgencyService;
	private final PickupAgencyService pickupAgencyService;

	public Mono<RequestWorkflowContext> cleanup(RequestWorkflowContext ctx) {
		return Mono.just(ctx)
			.flatMap(supplyingAgencyService::cleanUp)
			.flatMap(borrowingAgencyService::cleanUp)
			.flatMap(pickupAgencyService::cleanUp);
	}
}
