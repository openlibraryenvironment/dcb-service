package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class ReferenceValueMappingFixture {
	private final DataAccess dataAccess = new DataAccess();

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
