package org.olf.dcb.test;

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

	public void deleteAllReferenceValueMappings() {
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
}
