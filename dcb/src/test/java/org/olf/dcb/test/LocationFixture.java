package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.LocationSymbolRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class LocationFixture {
	private final DataAccess dataAccess = new DataAccess();

	@Inject
	private LocationRepository locationRepository;

	@Inject
	private LocationSymbolRepository locationSymbolRepository;

	public void deleteAll() {
		dataAccess.deleteAll(locationSymbolRepository.queryAll(),
			symbol -> locationSymbolRepository.delete(symbol.getId()));

		dataAccess.deleteAll(locationRepository.queryAll(),
			mapping -> locationRepository.delete(mapping.getId()));
	}

	public Location createPickupLocation(String name, String code) {
		return singleValueFrom(locationRepository.save(Location.builder()
				.id(UUID.randomUUID())
				.name(name)
				.code(code)
				.type("PICKUP")
				.isPickup(Boolean.TRUE)
				.isShelving(Boolean.TRUE)
				.isSupplyingLocation(Boolean.TRUE)
				.build()));
	}

	public Location createPickupLocation(String name, String code,
		double latitude, double longitude) {

		return singleValueFrom(locationRepository.save(Location.builder()
			.id(UUID.randomUUID())
			.name(name)
			.code(code)
			.type("PICKUP")
			.isPickup(Boolean.TRUE)
			.isShelving(Boolean.TRUE)
			.isSupplyingLocation(Boolean.TRUE)
			.latitude(latitude)
			.longitude(longitude)
			.build()));
	}

	public Location createPickupLocation(UUID uuid, String name, String code, DataAgency da) {
    return singleValueFrom(locationRepository.save(Location.builder()
        .id(uuid)
        .name(name)
        .code(code)
        .type("PICKUP")
				.isPickup(Boolean.TRUE)
				.isShelving(Boolean.TRUE)
				.isSupplyingLocation(Boolean.TRUE)
				.agency(da)
        .build()));
  }

	public Location createPickupLocation(DataAgency agency) {
		final var pickupLocationId = randomUUID();

		return createPickupLocation(pickupLocationId, "Pickup Location",
			"pickup-location", agency);
	}
}
