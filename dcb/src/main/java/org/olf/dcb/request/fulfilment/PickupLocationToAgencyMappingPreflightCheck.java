package org.olf.dcb.request.fulfilment;

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
		final var pickupLocationCode = command.getPickupLocation().getCode();

		return Mono.from(referenceValueMappingService.findLocationToAgencyMapping(pickupLocationCode))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not mapped to an agency"))
			.map(List::of);
	}
}
