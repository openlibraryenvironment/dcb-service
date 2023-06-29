package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;

import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

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
