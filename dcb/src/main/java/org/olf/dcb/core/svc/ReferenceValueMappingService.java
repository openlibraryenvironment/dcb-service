package org.olf.dcb.core.svc;

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

	public Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(String pickupLocationCode) {
		return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
			"PickupLocation",
			"DCB",
			pickupLocationCode,
			"AGENCY",
			"DCB"));
	}

        public Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(String context, String pickupLocationCode) {
                return Mono.from(repository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
                        "PickupLocation",
                        context,
                        pickupLocationCode,
                        "AGENCY",
                        "DCB"));
        }
}
