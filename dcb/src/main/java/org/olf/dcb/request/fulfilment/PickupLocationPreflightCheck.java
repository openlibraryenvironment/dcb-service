package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.svc.LocationService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.pickup-location.enabled", defaultValue = "true", notEquals = "false")
public class PickupLocationPreflightCheck implements PreflightCheck {
	private final LocationService locationService;

	public PickupLocationPreflightCheck(LocationService locationService) {
		this.locationService = locationService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocationCode();

		return locationService.findByCode(pickupLocationCode)
			.switchIfEmpty(Mono.defer(() -> { return locationService.findById(pickupLocationCode); }))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not a recognised pickup location code"))
			.map(List::of);
	}
}
