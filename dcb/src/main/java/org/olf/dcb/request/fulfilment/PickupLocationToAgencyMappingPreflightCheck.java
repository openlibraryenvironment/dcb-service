package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.pickup-location-to-agency-mapping.enabled", defaultValue = "true", notEquals = "false")
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;
	private final LocationService locationService;

	public PickupLocationToAgencyMappingPreflightCheck(
		LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService, LocationService locationService) {

		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
		this.locationService = locationService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {

		String pickupLocationCode = command.getPickupLocationCode();

		// return getAgencyForPickupLocation(pickupLocationCode)
		// 	.map( agency -> CheckResult.passed() )
		// 	.defaultIfEmpty("Pickup location \"" + pickupLocationCode + "\" is not mapped to an agency")
		// 	.map(List::of);

		return checkMapping(command)
			.flatMap(function(this::checkAgencyWhenPreviousCheckPassed))
		 	.map(List::of);
	}

	private Mono<DataAgency> getAgencyForPickupLocation(String locationCode) {
		return locationService.findById(locationCode)
			.map( location -> location.getAgency() )
			.map( agency -> agency.getId() )
			.flatMap ( agencyId -> agencyService.findById(agencyId) );
	}

	private Mono<Tuple3<CheckResult, String, String>> checkMapping(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocationCode();

		return findPickupLocationToAgencyMapping(command)
			.map(mapping -> Tuples.of(CheckResult.passed(), mapping.getToValue(), pickupLocationCode))
			.defaultIfEmpty(Tuples.of(CheckResult.failed(
				"Pickup location \"" + pickupLocationCode + "\" is not mapped to an agency"), "", pickupLocationCode));
	}

	private Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(
		PlacePatronRequestCommand command) {

		return mapPossibleIdToCode(command.getPickupLocationCode())
			.flatMap(locationCode -> locationToAgencyMappingService.findPickupLocationToAgencyMapping(locationCode,
				command.getPickupLocationContext(), command.getRequestorLocalSystemCode()));
	}

	/**
	 * // The code might be an ID, when it is find the location and use that code
	 *
	 * @param pickupLocationCode code to try to interpret as an ID
	 * @return the code of the location found by ID if the code is an ID,
	 * otherwise return the original code
	 */
	private Mono<String> mapPossibleIdToCode(String pickupLocationCode) {
		return locationService.findById(pickupLocationCode)
			.map(Location::getCode)
			.defaultIfEmpty(pickupLocationCode);
	}

	private Mono<CheckResult> checkAgencyWhenPreviousCheckPassed(CheckResult previousCheck,
		String agencyCode, String pickupLocationCode) {

		return Mono.just(previousCheck)
			.flatMap(check -> {
				if (previousCheck.getFailed()) {
					return Mono.just(previousCheck);
				}

				return checkAgency(agencyCode, pickupLocationCode);
			});
	}

	private Mono<CheckResult> checkAgency(String agencyCode, String pickupLocationCode) {
		return agencyService.findByCode(agencyCode)
			.map(mapping -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed(
				"Pickup location \"" + pickupLocationCode + "\" is mapped to \"" + agencyCode + "\" which is not a recognised agency"));
	}
}
