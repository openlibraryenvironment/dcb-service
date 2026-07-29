package org.olf.dcb.core.interaction;

import reactor.core.publisher.Mono;

public interface CanPlaceBorrowingAgencyRequest {
	Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters);
}
