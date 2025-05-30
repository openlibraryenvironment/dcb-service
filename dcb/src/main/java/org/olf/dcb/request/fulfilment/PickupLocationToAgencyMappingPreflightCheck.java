package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.utils.PropertyAccessUtils;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled", defaultValue = "true", notEquals = "false")
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	private final AgencyService agencyService;
	private final LocationService locationService;
  private final IntMessageService intMessageService;


	public PickupLocationToAgencyMappingPreflightCheck(
		LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService, LocationService locationService,
    IntMessageService intMessageService) {

		this.agencyService = agencyService;
		this.locationService = locationService;
    this.intMessageService = intMessageService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode);

		return getAgencyForPickupLocation(pickupLocationCode)
			.map(agency -> passed())
			.defaultIfEmpty(failedUm("PICKUP_LOCATION_NOT_MAPPED_TO_AGENCY",
				"Pickup location \"%s\" is not mapped to an agency".formatted(pickupLocationCode),
				intMessageService.getMessage("PICKUP_LOCATION_NOT_MAPPED_TO_AGENCY")
			))
			.map(List::of);
	}

	private Mono<DataAgency> getAgencyForPickupLocation(String locationCode) {
		return locationService.findById(locationCode)
			.map(Location::getAgency)
			.map(DataAgency::getId)
			.flatMap(agencyService::findById);
	}
}
