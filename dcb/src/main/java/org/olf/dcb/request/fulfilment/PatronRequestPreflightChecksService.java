package org.olf.dcb.request.fulfilment;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocation().getCode();

		if (Objects.equals(pickupLocationCode, "unknown-pickup-location")) {
			throw CheckFailedException.builder()
				.failedChecks(List.of(Check.builder()
					.failureDescription("\"" + pickupLocationCode + "\" is not a recognised pickup location code")
					.build()))
				.build();
		}

		return Mono.just(command);
	}
}
