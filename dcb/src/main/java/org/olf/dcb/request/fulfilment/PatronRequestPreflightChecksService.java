package org.olf.dcb.request.fulfilment;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private	final PickupLocationPreflightCheck pickupLocationPreflightCheck;

	public PatronRequestPreflightChecksService(PickupLocationPreflightCheck pickupLocationPreflightCheck) {
		this.pickupLocationPreflightCheck = pickupLocationPreflightCheck;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		return pickupLocationPreflightCheck.check(command)
			.flatMap(results -> {
				if (results.stream().allMatch(CheckResult::getPassed)) {
					return Mono.just(command);
				}

				final var failedChecks = results.stream()
					.filter(result -> !result.getPassed())
					.map(result -> FailedPreflightCheck.builder()
						.failureDescription(result.getFailureDescription())
						.build())
					.toList();

				return Mono.error(PreflightCheckFailedException.builder()
					.failedChecks(failedChecks)
					.build());
			});
	}
}
