package org.olf.dcb.request.fulfilment;

import java.util.List;

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
}
