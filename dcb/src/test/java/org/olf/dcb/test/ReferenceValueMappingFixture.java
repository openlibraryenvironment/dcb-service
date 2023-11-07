package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Prototype
public class ReferenceValueMappingFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	public ReferenceValueMappingFixture(
		ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericRangeMappingRepository numericRangeMappingRepository) {

		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(referenceValueMappingRepository.queryAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));

		dataAccess.deleteAll(numericRangeMappingRepository.queryAll(),
			mapping -> numericRangeMappingRepository.delete(mapping.getId()));
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

	public void defineLocationToAgencyMapping(String locationCode, String agencyCode) {
		defineLocationToAgencyMapping("DCB", locationCode, agencyCode);
	}

	public void defineLocationToAgencyMapping(String fromContext, String locationCode, String agencyCode) {
		log.debug("Define location mapping in tests with from context: {}, location code: {}, agency code: {}",
			fromContext, locationCode, agencyCode);

		final var mappingToSave = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("Location")
			.fromContext(fromContext)
			.fromValue(locationCode)
			.toCategory("AGENCY")
			.toContext("DCB")
			.toValue(agencyCode)
			.build();

		log.debug("Saving reference value mapping for tests: {}", mappingToSave);

		saveReferenceValueMapping(mappingToSave);
	}

/*
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
*/

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
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}

	public void defineNumericPatronTypeRangeMapping(String fromContext,
		long lowerBound,
		long upperBound,
		String targetContext,
		String targetValue) { 
		singleValueFrom(
			numericRangeMappingRepository.save(
				NumericRangeMapping.builder()
					.id(UUID.randomUUID())
					.context(fromContext)
					.domain("patronType")
					.lowerBound(lowerBound)
					.upperBound(upperBound)
					.targetContext(targetContext)
					.mappedValue(targetValue)
					.build()
			)
		);
	}
}
