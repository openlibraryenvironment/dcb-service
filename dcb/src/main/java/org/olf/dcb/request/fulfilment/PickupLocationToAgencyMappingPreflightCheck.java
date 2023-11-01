package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;

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
				"\"" + pickupLocationCode + "\" is not mapped to an agency"), "", pickupLocationCode));
	}

	private Mono<ReferenceValueMapping> findAgencyMapping(PlacePatronRequestCommand command) {
		return findAgencyMapping(command.getPickupLocationCode(), command.getPickupLocationContext(), command.getRequestorLocalSystemCode());
	}

	private Mono<ReferenceValueMapping> findAgencyMapping(String pickupLocationCode,
		String pickupLocationContext, String requestorLocalSystemCode) {
		
		return findDcbContextAgencyMapping(pickupLocationCode)
			.switchIfEmpty(findAgencyMapping(pickupLocationContext, pickupLocationCode))
			.switchIfEmpty(findAgencyMapping(requestorLocalSystemCode, pickupLocationCode));
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
				"\"" + pickupLocationCode + "\" is mapped to \"" + agencyCode + "\" which is not a recognised agency"));
	}
}
