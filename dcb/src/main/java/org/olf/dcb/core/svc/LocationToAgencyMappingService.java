package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
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
			.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode(rvm.getToValue())))
			.doOnNext(agency -> log.debug("Found agency for location: {}", agency));
	}

	public Mono<Item> enrichItemAgencyFromLocation(Item incomingItem, String hostLmsCode) {
		return Mono.just(incomingItem)
			.zipWhen(item -> findLocationToAgencyMapping(item, hostLmsCode), Item::setAgency)
			.defaultIfEmpty(incomingItem);
	}

	private Mono<DataAgency> findLocationToAgencyMapping(Item item, String hostLmsCode) {
		if (isEmpty(item.getLocationCode())) {
			return Mono.empty();
		}

		return mapLocationToAgency(hostLmsCode, trimToNull(item.getLocationCode()));
	}
}
