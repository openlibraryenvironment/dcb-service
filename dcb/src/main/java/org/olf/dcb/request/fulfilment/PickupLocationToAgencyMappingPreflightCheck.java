package org.olf.dcb.request.fulfilment;

import java.util.Collections;
import java.util.List;

import org.olf.dcb.core.svc.ReferenceValueMappingService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	private final ReferenceValueMappingService referenceValueMappingService;

	public PickupLocationToAgencyMappingPreflightCheck(ReferenceValueMappingService referenceValueMappingService) {
		this.referenceValueMappingService = referenceValueMappingService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return Mono.just(Collections.emptyList());
	}
}
