package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;
import reactor.core.publisher.Mono;

@Prototype
public class PatronTypeService {
	private final String fixedPatronType;

	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public PatronTypeService(
		@Value("${dcb.requests.supplying.patron-type:210}") String fixedPatronType,
		ReferenceValueMappingRepository referenceValueMappingRepository) {

		this.fixedPatronType = fixedPatronType;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Mono<String> determinePatronType(String hostLmsCode) {
		return findMapping(hostLmsCode)
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.just(fixedPatronType));
	}

	private Mono<ReferenceValueMapping> findMapping(String hostLmsCode) {
		return Mono.from(
			referenceValueMappingRepository.findByFromCategoryAndFromContextAndFromValueAndToContext(
				"patronType", "DCB", "DCB_UG", hostLmsCode));
	}
}
