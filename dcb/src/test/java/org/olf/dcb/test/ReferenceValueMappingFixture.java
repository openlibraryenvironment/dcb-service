package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ReferenceValueMappingFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final NumericRangeMappingFixture numericRangeMappingFixture;

	public ReferenceValueMappingFixture(
		ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericRangeMappingFixture numericRangeMappingFixture) {

		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericRangeMappingFixture = numericRangeMappingFixture;
	}

	public void deleteAll() {
		dataAccess.deleteAll(referenceValueMappingRepository.queryAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));

		numericRangeMappingFixture.deleteAll();
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

	public void defineNumericPatronTypeRangeMapping(String fromContext,
		long lowerBound, long upperBound, String targetContext, String targetValue) {

		numericRangeMappingFixture.createMapping(fromContext, "patronType",
			lowerBound, upperBound, targetContext, targetValue);
	}

  public void defineMapping(String fromContext, String fromCategory,
		String fromValue, String toContext, String toCategory, String toValue) {

		singleValueFrom(referenceValueMappingRepository.save(
			ReferenceValueMapping.builder()
				.id(randomUUID())
				.fromContext(fromContext)
				.fromCategory(fromCategory)
				.fromValue(fromValue)
				.toContext(toContext)
				.toCategory(toCategory)
				.toValue(toValue)
				.build()));
  }

	public void defineLocalToCanonicalItemTypeRangeMapping(String hostLmsCode,
		long lowerBound, long upperBound, String canonicalItemType) {

		numericRangeMappingFixture.createMapping(hostLmsCode, "ItemType",
			lowerBound, upperBound, "DCB", canonicalItemType);
	}

	public void defineLocalToCanonicalItemTypeMapping(String hostLmsCode,
		String localItemType, String canonicalItemType) {

		singleValueFrom(referenceValueMappingRepository.save(
			ReferenceValueMapping.builder()
				.id(randomUUID())
				.fromContext(hostLmsCode)
				.fromCategory("ItemType")
				.fromValue(localItemType)
				.toContext("DCB")
				.toCategory("ItemType")
				.toValue(canonicalItemType)
				.reciprocal(true)
				.build()));
	}

	public void defineCanonicalToLocalItemTypeMapping(String toContext,
		String canonicalItemType, String localItemType) {

		defineMapping("DCB", "ItemType", canonicalItemType,
			toContext, "ItemType", localItemType);
	}

	private void saveReferenceValueMapping(ReferenceValueMapping mapping) {
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}

	public void defineMapping(String fromContext, String fromCategory, String fromValue,
		String toContext, String toCategory, String toValue, boolean reciprocal) {
		
		log.debug("defineMapping({}, {}, {}, {}, {}, {}, {})",
			fromContext, fromCategory, fromValue, toContext, toCategory, toValue, reciprocal);

		singleValueFrom(referenceValueMappingRepository.save(
			ReferenceValueMapping.builder()
				.id(randomUUID())
				.fromContext(fromContext)
				.fromCategory(fromCategory)
				.fromValue(fromValue)
				.toContext(toContext)
				.toCategory(toCategory)
				.toValue(toValue)
				.reciprocal(reciprocal)
				.build()));
	}
}
