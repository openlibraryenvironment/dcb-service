package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.HostLmsService;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronTypeService {
	private final HostLmsService hostLmsService;

	public PatronTypeService(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	/**
	 * N.B. The structure of the mappings table is to go from a source context to a target context. In order to prevent
	 * an explosion of mappings, we introduce the "Central"/"Core"/"Spine" context so we can map from a source into the spine
	 * and then from the spine to the target. We label our spine context "DCB" and this should be considered the canonical
	 * context over the DCB system.
	 */
	public Mono<String> determinePatronType(String supplierHostLmsCode,
		String requesterHostLmsCode, String requesterPatronType, String requesterLocalId) {

		log.debug("determinePatronType supplier={} requester={} ptype={}",
			supplierHostLmsCode, requesterHostLmsCode, requesterPatronType);

		return findCanonicalPatronType(requesterHostLmsCode, requesterPatronType, requesterLocalId)
			.flatMap(canonicalPatronType -> findLocalPatronType(supplierHostLmsCode, canonicalPatronType))
			.switchIfEmpty(Mono.error(new PatronTypeMappingNotFound("No mapping found from ptype " +
				requesterHostLmsCode + ":" + requesterPatronType + " to " + supplierHostLmsCode)))
			.onErrorMap(cause -> new PatronTypeMappingNotFound("No mapping found from ptype " +
				requesterHostLmsCode + ":" + requesterPatronType + " to " + supplierHostLmsCode + " because " + cause.getMessage()));
	}

	private Mono<String> findLocalPatronType(String supplierHostLmsCode,
		String canonicalPatronType) {

		return hostLmsService.getClientFor(supplierHostLmsCode)
			.flatMap(client -> client.findLocalPatronType(canonicalPatronType));
	}

	private Mono<String> findCanonicalPatronType(String requesterHostLmsCode,
		String requesterPatronType, String requesterLocalId) {

		return hostLmsService.getClientFor(requesterHostLmsCode)
			.flatMap(client -> client.findCanonicalPatronType(requesterPatronType, requesterLocalId));
	}
}
