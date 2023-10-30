package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private final LocationRepository locationRepository;

	public PatronRequestPreflightChecksService(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		return checkForPickupLocation(command)
			.flatMap(result -> {
				if (result.getPassed()) {
					return Mono.just(command);
				}
				else {
					return Mono.error(PreflightCheckFailedException.builder()
						.failedChecks(List.of(
							FailedPreflightCheck.builder()
								.failureDescription(result.getFailureDescription())
								.build()))
						.build());
				}
			});
	}

	private Mono<CheckResult> checkForPickupLocation(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocation().getCode();

		return Mono.from(locationRepository.findOneByCode(pickupLocationCode))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not a recognised pickup location code"));
	}
}
