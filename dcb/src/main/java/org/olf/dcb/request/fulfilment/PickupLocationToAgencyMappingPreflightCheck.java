package org.olf.dcb.request.fulfilment;

import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return Mono.just(Collections.emptyList());
	}
}
