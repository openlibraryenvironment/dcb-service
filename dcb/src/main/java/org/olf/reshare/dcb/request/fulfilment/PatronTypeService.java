package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Prototype
public class PatronTypeService {
	private final String fixedPatronType;

	private final ReferenceValueMappingRepository referenceValueMappingRepository;

        private static final Logger log =
                LoggerFactory.getLogger(PatronTypeService.class);


	public PatronTypeService(
		@Value("${dcb.requests.supplying.patron-type:210}") String fixedPatronType,
		ReferenceValueMappingRepository referenceValueMappingRepository) {

		this.fixedPatronType = fixedPatronType;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

        /**
         * N.B. The structure of the mappings table is to go from a source context to a target context. In order to prevent
         * an explosion of mappings, we introduce the "Central"/"Core"/"Spine" context so we can map from a source into the spine
         * and then from the spine to the target. We label our spine context "DCB" and this should be considered the canonical
         * context over the DCB system.
         */
	public Mono<String> determinePatronType(String supplierHostLmsCode, String requesterHostLmsCode, String requesterPatronType) {
                log.debug("determinePatronType {} {} {}",supplierHostLmsCode,requesterHostLmsCode,requesterPatronType);
                // Get the "Spine" mapping
		return findMapping("DCB",requesterHostLmsCode, requesterPatronType)
                        .doOnNext ( spineMapping -> log.debug("Got spine mapping {}",spineMapping) )
                        .flatMap( spineMapping -> findMapping( supplierHostLmsCode, "DCB", spineMapping.getToValue() ) )
                        .doOnNext ( targetMapping -> log.debug("Got target mapping {}",targetMapping) )
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.just(fixedPatronType));

                        // TODO: Replace the above switchIfEmpty with and update tests.
			// .switchIfEmpty(Mono.error(new RuntimeException("No mapping found from ptype "+requesterHostLmsCode+":"+requesterPatronType+" to "+supplierHostLmsCode)));
	}

	private Mono<ReferenceValueMapping> findMapping(String targetContext, String sourceContext, String sourceValue) {
                log.debug("findMapping {} {} {}",targetContext,sourceContext,sourceValue);
		return Mono.from(
			referenceValueMappingRepository.findByFromCategoryAndFromContextAndFromValueAndToContext(
				"patronType", sourceContext, sourceValue, targetContext));
	}
}
