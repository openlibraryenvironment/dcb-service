package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Singleton
public class NumericRangeMappingFixture {
	private final DataAccess dataAccess = new DataAccess();
	private final NumericRangeMappingRepository repository;

	public NumericRangeMappingFixture(NumericRangeMappingRepository repository) {
		this.repository = repository;
	}

	public void createNumericRangeMapping(String system, String domain,
		Long lowerBound, Long upperBound, String targetContext, String targetValue) {

		log.debug("createNumericRangeMapping({},{},{},{},{},{})", system, domain,
			lowerBound, upperBound, targetContext, targetValue);

		final var nrm = NumericRangeMapping.builder()
			.id(UUIDUtils.dnsUUID(system + ":" + ":" + domain + ":" + targetContext + ":" + lowerBound))
			.context(system)
			.domain(domain)
			.lowerBound(lowerBound)
			.upperBound(upperBound)
			.targetContext(targetContext)
			.mappedValue(targetValue)
			.build();

		singleValueFrom(repository.save(nrm));
	}

	public void deleteAll() {
		dataAccess.deleteAll(repository.queryAll(),
			mapping -> repository.delete(mapping.getId()));
	}
}
