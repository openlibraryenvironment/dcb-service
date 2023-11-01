package org.olf.dcb.request.fulfilment;

import static reactor.function.TupleUtils.function;

import java.util.List;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.core.util.StringUtils;
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
		return findDcbContextAgencyMapping(command.getPickupLocationCode())
			.switchIfEmpty(findAgencyMapping(command.getPickupLocationContext(), command.getPickupLocationCode()));
	}

	private Mono<ReferenceValueMapping> findAgencyMapping(
		String pickupLocationContext, String pickupLocationCode) {

		if (StringUtils.isEmpty(pickupLocationContext)) {
			return Mono.empty();
		}

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
		return findAgency(agencyCode)
			.map(mapping -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed(
				"\"" + pickupLocationCode + "\" is mapped to \"" + agencyCode + "\" which is not a recognised agency"));
	}

	private Mono<DataAgency> findAgency(String agencyCode) {
		return Mono.from(agencyRepository.findOneByCode(agencyCode));
	}
}
