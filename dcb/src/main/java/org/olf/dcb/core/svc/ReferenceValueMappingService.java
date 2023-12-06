package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ReferenceValueMappingService {
	private final ReferenceValueMappingRepository repository;

	public ReferenceValueMappingService(ReferenceValueMappingRepository repository) {
		this.repository = repository;
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String pickupLocationCode) {
		return findLocationToAgencyMapping("DCB", pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String locationCode) {
		if (isEmpty(fromContext)) {
			log.warn("Attempting to find mapping from location (code: \"{}\") to agency with empty from context", locationCode);

			return Mono.empty();
		}

		final var fromCategory = "Location";
		final var toCategory = "AGENCY";
		final var toContext = "DCB";

		log.debug("Attempting to find mapping from category: {}, from context: {}, location code: {}, to category: {}, to context: {}",
			fromCategory, fromContext, locationCode, toCategory, toContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
			fromCategory, fromContext, locationCode, toCategory, toContext));
	}

	public Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(
		String pickupLocationContext, String pickupLocationCode) {

		return findLocationToAgencyMapping(pickupLocationContext, pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(
		String pickupLocationCode, String pickupLocationContext, String requestorLocalSystemCode) {

		return findLocationToAgencyMapping(pickupLocationCode)
			.switchIfEmpty(Mono.defer(() -> findPickupLocationToAgencyMapping(pickupLocationContext, pickupLocationCode)))
			.switchIfEmpty(Mono.defer(() -> findPickupLocationToAgencyMapping(requestorLocalSystemCode, pickupLocationCode)))
			.doOnSuccess( result -> {
				if ( result != null ) {
					log.debug("Found mapping: {}", result);
				}
				else {
					log.info("No pickup location mapping found for {} {} {}",pickupLocationCode,pickupLocationContext,requestorLocalSystemCode);
				}
			} );
	}
}
