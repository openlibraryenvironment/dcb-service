package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Singleton
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	private final ReferenceValueMappingService referenceValueMappingService;
	private final AgencyService agencyService;
	private final LocationService locationService;

	public PickupLocationToAgencyMappingPreflightCheck(
			ReferenceValueMappingService referenceValueMappingService,
			AgencyService agencyService, LocationService locationService) {

		this.referenceValueMappingService = referenceValueMappingService;
		this.agencyService = agencyService;
		this.locationService = locationService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return checkMapping(command)
			.flatMap(function(this::checkAgencyWhenPreviousCheckPassed))
			.map(List::of);
	}

	private Mono<Tuple3<CheckResult, String, String>> checkMapping(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocationCode();

		return findAgencyMapping(command)
			.map(mapping -> Tuples.of(CheckResult.passed(), mapping.getToValue(), pickupLocationCode))
			.defaultIfEmpty(Tuples.of(CheckResult.failed(
				"Pickup location \"" + pickupLocationCode + "\" is not mapped to an agency"), "", pickupLocationCode));
	}

	private Mono<ReferenceValueMapping> findAgencyMapping(PlacePatronRequestCommand command) {
		return mapPossibleIdToCode(command.getPickupLocationCode())
			.flatMap(locationCode -> findAgencyMapping(locationCode,
				command.getPickupLocationContext(), command.getRequestorLocalSystemCode()));
	}

	private Mono<ReferenceValueMapping> findAgencyMapping(String pickupLocationCode,
		String pickupLocationContext, String requestorLocalSystemCode) {
		
		return findDcbContextAgencyMapping(pickupLocationCode)
			.switchIfEmpty(Mono.defer(() -> findAgencyMapping(pickupLocationContext, pickupLocationCode)))
			.switchIfEmpty(Mono.defer(() -> findAgencyMapping(requestorLocalSystemCode, pickupLocationCode)));
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

	private Mono<ReferenceValueMapping> findAgencyMapping(
		String pickupLocationContext, String pickupLocationCode) {

		return referenceValueMappingService.findLocationToAgencyMapping(
			pickupLocationContext, pickupLocationCode);
	}

	private Mono<ReferenceValueMapping> findDcbContextAgencyMapping(String pickupLocationCode) {
		return Mono.from(referenceValueMappingService.findLocationToAgencyMapping(pickupLocationCode));
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
