package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class ReferenceValueMappingFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ReferenceValueMappingRepository repository;

	public ReferenceValueMappingFixture(ReferenceValueMappingRepository repository) {
		this.repository = repository;
	}

	public void saveReferenceValueMapping(ReferenceValueMapping mapping) {
		singleValueFrom(repository.save(mapping));
	}

	public void deleteAll() {
		dataAccess.deleteAll(repository.queryAll(),
			mapping -> repository.delete(mapping.getId()));
	}

	public void defineItemStatusMapping(String fromHostLmsCode, String fromValue, String toValue) {
		final var mapping = ReferenceValueMapping.builder()
			.id(UUID.randomUUID())
			.fromCategory("itemStatus")
			.fromContext(fromHostLmsCode)
			.fromValue(fromValue)
			.toCategory("itemStatus")
			.toContext("DCB")
			.toValue(toValue)
			.reciprocal(true)
			.build();

		saveReferenceValueMapping(mapping);
	}

	public void definePickupLocationToAgencyMapping(String pickupLocationCode, String agencyCode) {
		final var mapping = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("PickupLocation")
			.fromContext("DCB")
			.fromValue(pickupLocationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build();

		saveReferenceValueMapping(mapping);
	}

	public void defineShelvingLocationToAgencyMapping(String fromContext,
		String shelvingLocationCode, String agencyCode) {

		final var mapping = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("ShelvingLocation")
			.fromContext(fromContext)
			.fromValue(shelvingLocationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build();

		saveReferenceValueMapping(mapping);
	}

	public void defineLocationToAgencyMapping(String fromContext,
		String locationCode, String agencyCode) {

		final var mapping = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("Location")
			.fromContext(fromContext)
			.fromValue(locationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build();

		saveReferenceValueMapping(mapping);
	}
}
