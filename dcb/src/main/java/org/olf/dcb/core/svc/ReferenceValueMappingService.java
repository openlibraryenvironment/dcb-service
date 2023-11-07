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

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String pickupLocationCode) {
		if (isEmpty(fromContext)) {
			return Mono.empty();
		}

		final var fromCategory = "Location";
		final var toCategory = "AGENCY";
		final var toContext = "DCB";

		log.debug("Attempting to find mapping from category: {}, from context: {}, pickup location code: {}, to category: {}, to context: {}",
			fromCategory, fromContext, pickupLocationCode, toCategory, toContext);

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
			fromCategory, fromContext, pickupLocationCode, toCategory, toContext))
			.doOnSuccess(mapping -> log.debug("Found mapping: {}", mapping));
	}
}
