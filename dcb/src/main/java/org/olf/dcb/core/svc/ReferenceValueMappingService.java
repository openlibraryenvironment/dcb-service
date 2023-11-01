package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ReferenceValueMappingService {
	private final ReferenceValueMappingRepository repository;

	public ReferenceValueMappingService(ReferenceValueMappingRepository repository) {
		this.repository = repository;
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String pickupLocationCode) {
		return findLocationToAgencyMapping("DCB", pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String context, String pickupLocationCode) {
		if (isEmpty(context)) {
			return Mono.empty();
		}

		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
			"Location",
			context,
			pickupLocationCode,
			"AGENCY",
			"DCB"));
	}
}
