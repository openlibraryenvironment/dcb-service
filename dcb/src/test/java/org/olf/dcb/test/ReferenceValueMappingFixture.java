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

	public void deleteAll() {
		dataAccess.deleteAll(repository.queryAll(),
			mapping -> repository.delete(mapping.getId()));
	}

	public void defineItemStatusMapping(String fromHostLmsCode, String fromValue, String toValue) {
		saveReferenceValueMapping(ReferenceValueMapping.builder()
			.id(UUID.randomUUID())
			.fromCategory("itemStatus")
			.fromContext(fromHostLmsCode)
			.fromValue(fromValue)
			.toCategory("itemStatus")
			.toContext("DCB")
			.toValue(toValue)
			.reciprocal(true)
			.build());
	}

	public void definePickupLocationToAgencyMapping(String pickupLocationCode, String agencyCode) {
		saveReferenceValueMapping(ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("PickupLocation")
			.fromContext("DCB")
			.fromValue(pickupLocationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build());
	}

	public void defineShelvingLocationToAgencyMapping(String fromContext,
		String shelvingLocationCode, String agencyCode) {

		saveReferenceValueMapping(ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("ShelvingLocation")
			.fromContext(fromContext)
			.fromValue(shelvingLocationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build());
	}

	public void defineLocationToAgencyMapping(String fromContext,
		String locationCode, String agencyCode) {

		saveReferenceValueMapping(ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("Location")
			.fromContext(fromContext)
			.fromValue(locationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build());
	}

	public void definePatronTypeMapping(String fromContext, String fromPatronType,
		String toContext, String toPatronType) {

		saveReferenceValueMapping(
			ReferenceValueMapping.builder()
				.id(UUID.randomUUID())
				.fromCategory("patronType")
				.fromContext(fromContext)
				.fromValue(fromPatronType)
				.toCategory("patronType")
				.toContext(toContext)
				.toValue(toPatronType)
				.reciprocal(true)
				.build());
	}

	private void saveReferenceValueMapping(ReferenceValueMapping mapping) {
		singleValueFrom(repository.save(mapping));
	}
}
