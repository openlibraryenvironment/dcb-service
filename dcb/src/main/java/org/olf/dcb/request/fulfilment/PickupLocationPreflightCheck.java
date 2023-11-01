package org.olf.dcb.request.fulfilment;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PickupLocationPreflightCheck implements PreflightCheck {
	private final LocationRepository locationRepository;

	public PickupLocationPreflightCheck(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = command.getPickupLocationCode();

		return findByCode(pickupLocationCode)
			.switchIfEmpty(findById(pickupLocationCode))
			.map(location -> CheckResult.passed())
			.defaultIfEmpty(CheckResult.failed("\"" + pickupLocationCode + "\" is not a recognised pickup location code"))
			.map(List::of);
	}

	/**
	 * Use the pickup location code which can sometimes be an ID to find a location record
	 *
	 * @param pickupLocationCode the code might actually be an ID
	 * @return empty if the code is not a UUID, otherwise the result of finding a location by ID
	 */
	private Mono<Location> findById(String pickupLocationCode) {
		try {
			final var id = UUID.fromString(pickupLocationCode);

			return Mono.from(locationRepository.findById(id));
		}
		// Code is not a UUID
		catch (IllegalArgumentException e) {
			return Mono.empty();
		}
	}

	private Mono<Location> findByCode(String pickupLocationCode) {
		return Mono.from(locationRepository.findOneByCode(pickupLocationCode));
	}
}
