package org.olf.dcb.test;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

@Prototype
public class ReferenceValueMappingFixture {
	private final DataAccess dataAccess = new DataAccess();
	@Inject
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public ReferenceValueMappingFixture(ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public void saveReferenceValueMapping(ReferenceValueMapping mapping) {
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}

	public void deleteAllReferenceValueMappings() {
		dataAccess.deleteAll(referenceValueMappingRepository.findAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));
	}
}
