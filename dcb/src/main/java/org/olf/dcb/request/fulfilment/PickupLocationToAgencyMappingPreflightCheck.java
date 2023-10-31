package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Singleton
public class PickupLocationToAgencyMappingPreflightCheck implements PreflightCheck {
	private final ReferenceValueMappingService referenceValueMappingService;
	private final AgencyRepository agencyRepository;

	public PickupLocationToAgencyMappingPreflightCheck(
		ReferenceValueMappingService referenceValueMappingService,
		AgencyRepository agencyRepository) {

		this.referenceValueMappingService = referenceValueMappingService;
		this.agencyRepository = agencyRepository;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocation().getCode();

		return checkMapping(pickupLocationCode)
			.map(Tuple2::getT1)
			.map(List::of);
	}

	private Mono<Tuple3<CheckResult, String, String>> checkMapping(String pickupLocationCode) {
		return Mono.from(referenceValueMappingService.findLocationToAgencyMapping(pickupLocationCode))
			.map(mapping -> Tuples.of(CheckResult.passed(), mapping.getToValue(), pickupLocationCode))
			.defaultIfEmpty(Tuples.of(CheckResult.failed("\"" + pickupLocationCode + "\" is not mapped to an agency"), "", pickupLocationCode));
	}
}
