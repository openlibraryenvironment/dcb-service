package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;

@Prototype
public class PatronTypeService {
	private static final Logger log = LoggerFactory.getLogger(PatronTypeService.class);
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final NumericPatronTypeMapper numericPatronTypeMapper;

	public PatronTypeService(ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericPatronTypeMapper numericPatronTypeMapper) {

		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
	}

	/**
	 * N.B. The structure of the mappings table is to go from a source context to a target context. In order to prevent
	 * an explosion of mappings, we introduce the "Central"/"Core"/"Spine" context so we can map from a source into the spine
	 * and then from the spine to the target. We label our spine context "DCB" and this should be considered the canonical
	 * context over the DCB system.
	 */
	public Mono<String> determinePatronType(String supplierHostLmsCode, String requesterHostLmsCode,
		String requesterPatronType) {
		log.debug("determinePatronType supplier={} requester={} ptype={}",supplierHostLmsCode,requesterHostLmsCode,requesterPatronType);
		// Get the "Spine" mapping
		return numericPatronTypeMapper.getCanonicalItemType(requesterHostLmsCode,requesterPatronType)
			.doOnNext ( spineMapping -> log.debug("Got spine mapping {}",spineMapping) )
			.flatMap( spineMapping -> findMapping( supplierHostLmsCode, "DCB", spineMapping ) )
			.doOnNext ( targetMapping -> log.debug("Got target mapping {}",targetMapping) )
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(
				new PatronTypeMappingNotFound("No mapping found from ptype " +
					requesterHostLmsCode+":" +requesterPatronType+" to "+supplierHostLmsCode)));
	}

	private Mono<ReferenceValueMapping> findMapping(String targetContext, String sourceContext, String sourceValue) {
                log.debug("findMapping targetCtx={} sourceCtx={} value={}",targetContext,sourceContext,sourceValue);
		return Mono.from(
			referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
				"patronType", sourceContext, sourceValue, targetContext));
	}
}
