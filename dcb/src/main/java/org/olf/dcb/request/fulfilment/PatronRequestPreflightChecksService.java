package org.olf.dcb.request.fulfilment;

import java.util.List;
import java.util.Objects;

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
		final var pickupLocationCode = command.getPickupLocation().getCode();

		if (Objects.equals(pickupLocationCode, "unknown-pickup-location")) {
			throw PreflightCheckFailedException.builder()
				.failedChecks(List.of(
					FailedPreflightCheck.builder()
						.failureDescription("\"" + pickupLocationCode + "\" is not a recognised pickup location code")
					.build()))
				.build();
		}

		return Mono.just(command);
	}
}
