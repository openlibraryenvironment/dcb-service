package org.olf.dcb.core.interaction;

import reactor.core.publisher.Mono;

public interface CanPlaceSupplyingAgencyRequest {
	Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters);
}
