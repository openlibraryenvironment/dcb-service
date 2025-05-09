package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.IntMessageService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.pickup-location.enabled", defaultValue = "true", notEquals = "false")
public class PickupLocationPreflightCheck implements PreflightCheck {

	private final LocationService locationService;
  private final IntMessageService intMessageService;

	public PickupLocationPreflightCheck(LocationService locationService,
		IntMessageService intMessageService) {
		this.locationService = locationService;
		this.intMessageService = intMessageService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode);

		return locationService.findByIdOrCode( pickupLocationCode )
			.map(location -> passed())
			.defaultIfEmpty(failedUm("UNKNOWN_PICKUP_LOCATION_CODE",
				"\"%s\" is not a recognised pickup location code".formatted(pickupLocationCode),
        intMessageService.getMessage("UNKNOWN_PICKUP_LOCATION_CODE")
				))
			.map(List::of);
	}
}
