package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.LocationRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class LocationFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final LocationRepository locationRepository;

	public LocationFixture(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(locationRepository.queryAll(),
			mapping -> locationRepository.delete(mapping.getId()));
	}

	public Location createPickupLocation(String name, String code) {
		return singleValueFrom(locationRepository.save(Location.builder()
				.id(UUID.randomUUID())
				.name(name)
				.code(code)
				.type("PICKUP")
				.build()));
	}

  public Location createPickupLocation(UUID uuid, String name, String code, DataAgency da) {
    return singleValueFrom(locationRepository.save(Location.builder()
        .id(uuid)
        .name(name)
        .code(code)
        .type("PICKUP")
				.agency(da)
        .build()));
  }

}
