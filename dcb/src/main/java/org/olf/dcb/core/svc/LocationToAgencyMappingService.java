package org.olf.dcb.core.svc;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class LocationToAgencyMappingService {
	private final AgencyRepository agencyRepository;
	private final ReferenceValueMappingService referenceValueMappingService;

	public LocationToAgencyMappingService(AgencyRepository agencyRepository,
		ReferenceValueMappingService referenceValueMappingService) {

		this.agencyRepository = agencyRepository;
		this.referenceValueMappingService = referenceValueMappingService;
	}

	public Mono<DataAgency> mapLocationToAgency(String hostLmsCode, String locationCode) {
		return referenceValueMappingService.findLocationToAgencyMapping(hostLmsCode, locationCode)
			.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode(rvm.getToValue())));
	}

	public Mono<DataAgency> findLocationToAgencyMapping(Item item, String hostLmsCode) {
		return mapLocationToAgency(hostLmsCode, item.getLocation().getCode().trim());
	}
}
