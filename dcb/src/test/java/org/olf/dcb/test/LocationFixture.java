package org.olf.dcb.test;

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
}
