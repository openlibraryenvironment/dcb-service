package org.olf.dcb.core.svc;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class LocationToAgencyMappingService {
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final AgencyRepository agencyRepository;

	public LocationToAgencyMappingService(
		ReferenceValueMappingRepository referenceValueMappingRepository,
		AgencyRepository agencyRepository) {

		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.agencyRepository = agencyRepository;
	}

	public Mono<DataAgency> mapLocationToAgency(String hostLmsCode, String location) {
		return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				"Location", hostLmsCode, location, "AGENCY", "DCB"))
			.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode(rvm.getToValue())));
	}
}
