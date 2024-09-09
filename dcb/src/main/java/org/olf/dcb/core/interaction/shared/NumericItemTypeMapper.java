package org.olf.dcb.core.interaction.shared;

import org.olf.dcb.storage.NumericRangeMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import reactor.core.publisher.Mono;

import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Singleton
public class NumericItemTypeMapper {
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	public NumericItemTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item, String hostLmsCode) {
		return getCanonicalItemType(hostLmsCode, item.getLocalItemTypeCode())
			.switchIfEmpty(raiseError(Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("Unexpected failure")
				.with("hostLmsCode", hostLmsCode)
				.with("localItemTypeCode", item.getLocalItemTypeCode())
				.build())
			)
			.map(item::setCanonicalItemType);
	}

	private Mono<String> getCanonicalItemType(String system, String localItemTypeCode) {
		log.debug("getCanonicalItemType({}, {})", system, localItemTypeCode);

		if (system == null) {
			log.error("No hostLmsCode provided");
			return raiseError(Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("No hostLmsCode provided")
				.build());
		}

		if (localItemTypeCode == null) {
			log.error("No localItemType provided");
			return raiseError(Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("No localItemType provided")
				.with("hostLmsCode", system)
				.build());
		}

		// Sierra item types are integers. They are usually mapped by a range
		// I have a feeling that creating a static cache of system->localItemType mappings will have solid performance
		// benefits
		try {
			Long l = Long.valueOf(localItemTypeCode);
			log.debug("Look up item type {}", l);
			return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "ItemType", "DCB", l))
				.doOnNext(nrm -> log.debug("nrm: {}", nrm))
				.switchIfEmpty(raiseError(Problem.builder()
					.withTitle("NumericItemTypeMapper")
					.withDetail("No canonical item type found for localItemTypeCode " + l)
					.with("hostLmsCode", system)
					.build())
				);
		} catch (Exception e) {
			log.error("Problem trying to convert {} into  long value", localItemTypeCode);
			return raiseError(Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("Problem trying to convert " + localItemTypeCode + " into  long value")
				.with("hostLmsCode", system)
				.with("Error", e.toString())
				.build());
		}
	}
}
