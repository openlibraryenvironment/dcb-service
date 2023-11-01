package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.svc.LocationService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PickupLocationPreflightCheck implements PreflightCheck {
	private final LocationService locationService;

	public PickupLocationPreflightCheck(LocationService locationService) {
		this.locationService = locationService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocationCode();

		return locationService.findByCode(pickupLocationCode)
			.switchIfEmpty(locationService.findById(pickupLocationCode))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not a recognised pickup location code"))
			.map(List::of);
	}
}
