package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronTypeService {
	private final ReferenceValueMappingService referenceValueMappingService;
	private final NumericPatronTypeMapper numericPatronTypeMapper;

	public PatronTypeService(ReferenceValueMappingService referenceValueMappingService,
		NumericPatronTypeMapper numericPatronTypeMapper) {

		this.referenceValueMappingService = referenceValueMappingService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
	}

	/**
	 * N.B. The structure of the mappings table is to go from a source context to a target context. In order to prevent
	 * an explosion of mappings, we introduce the "Central"/"Core"/"Spine" context so we can map from a source into the spine
	 * and then from the spine to the target. We label our spine context "DCB" and this should be considered the canonical
	 * context over the DCB system.
	 */
	public Mono<String> determinePatronType(String supplierHostLmsCode, String requesterHostLmsCode,
		String requesterPatronType, String requesterLocalId) {

		log.debug("determinePatronType supplier={} requester={} ptype={}",supplierHostLmsCode,requesterHostLmsCode,requesterPatronType);
		// Get the "Spine" mapping
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(requesterHostLmsCode,requesterPatronType, requesterLocalId)
			.doOnNext ( spineMapping -> log.debug("Got spine mapping {}",spineMapping) )
			.flatMap( spineMapping -> findMapping( supplierHostLmsCode, spineMapping ) )
			.doOnNext ( targetMapping -> log.debug("Got target mapping {}",targetMapping) )
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(
				new PatronTypeMappingNotFound("No mapping found from ptype " +
					requesterHostLmsCode+":" +requesterPatronType+" to "+supplierHostLmsCode)))
			.onErrorMap(cause -> new PatronTypeMappingNotFound("No mapping found from ptype " +
				requesterHostLmsCode + ":" + requesterPatronType + " to " + supplierHostLmsCode + " because " + cause.getMessage()));
	}

	private Mono<ReferenceValueMapping> findMapping(String targetContext, String sourceValue) {
		return referenceValueMappingService.findMapping("patronType", "DCB",
			sourceValue, targetContext);
	}

	public Mono<String> findCanonicalPatronType(String hostLmsCode, String localPatronType) {
		if (localPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType",
				hostLmsCode, localPatronType, "patronType", "DCB")
			.map(ReferenceValueMapping::getToValue);
	}
}
