package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

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
		final var pickupLocationCode = getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode);

		return locationService.findByIdOrCode( pickupLocationCode )
			.map(location -> passed())
			.defaultIfEmpty(failed("UNKNOWN_PICKUP_LOCATION_CODE",
				"\"%s\" is not a recognised pickup location code"
					.formatted(pickupLocationCode)))
			.map(List::of);
	}
}
