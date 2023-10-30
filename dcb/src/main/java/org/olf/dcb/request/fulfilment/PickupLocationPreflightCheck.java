package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PickupLocationPreflightCheck {
	private final LocationRepository locationRepository;

	public PickupLocationPreflightCheck(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocation().getCode();

		return Mono.from(locationRepository.findOneByCode(pickupLocationCode))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not a recognised pickup location code"))
			.map(List::of);
	}
}
