package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
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
			.flatMap(function(this::checkAgency))
			.map(List::of);
	}

	private Mono<Tuple3<CheckResult, String, String>> checkMapping(String pickupLocationCode) {
		return Mono.from(referenceValueMappingService.findLocationToAgencyMapping(pickupLocationCode))
			.map(mapping -> Tuples.of(CheckResult.passed(), mapping.getToValue(), pickupLocationCode))
			.defaultIfEmpty(Tuples.of(CheckResult.failed(
				"\"" + pickupLocationCode + "\" is not mapped to an agency"), "", pickupLocationCode));
	}

	private Mono<CheckResult> checkAgency(CheckResult previousCheck,
		String agencyCode, String pickupLocationCode) {

		return Mono.just(previousCheck)
			.flatMap(check -> {
				if (previousCheck.getFailed()) {
					return Mono.just(previousCheck);
				}

				return Mono.from(agencyRepository.findOneByCode(agencyCode))
					.map(mapping -> CheckResult.passed())
					.defaultIfEmpty(CheckResult.failed(
						"\"" + pickupLocationCode + "\" is mapped to \"" + agencyCode + "\" which is not a recognised agency"));
			});
	}
}
