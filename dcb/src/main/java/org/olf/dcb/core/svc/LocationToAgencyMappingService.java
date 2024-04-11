package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.core.publisher.Mono.empty;
import static reactor.function.TupleUtils.function;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class LocationToAgencyMappingService {
	private final AgencyService agencyService;
	private final ReferenceValueMappingService referenceValueMappingService;

	public LocationToAgencyMappingService(AgencyService agencyService,
		ReferenceValueMappingService referenceValueMappingService) {

		this.agencyService = agencyService;
		this.referenceValueMappingService = referenceValueMappingService;
	}

	public Mono<Item> enrichItemAgencyFromLocation(Item incomingItem, String hostLmsCode) {
		return Mono.just(incomingItem)
			.zipWhen(item -> findLocationToAgencyMapping(item, hostLmsCode))
			.map(function(Item::setAgency))
			.defaultIfEmpty(incomingItem);
	}

	private Mono<DataAgency> findLocationToAgencyMapping(Item item, String hostLmsCode) {
		final var locationCode = trimToNull(getValue(item, Item::getLocationCode));

		if (isEmpty(locationCode)) {
			return empty();
		}

		return mapLocationToAgency(hostLmsCode, locationCode);
	}

	private Mono<DataAgency> mapLocationToAgency(String hostLmsCode, String locationCode) {
		return findLocationToAgencyMapping(hostLmsCode, locationCode)
			.map(ReferenceValueMapping::getToValue)
			.flatMap(agencyService::findByCode)
			.doOnNext(agency -> log.debug("Found agency for location: {}", agency));
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String pickupLocationCode) {
		return findLocationToAgencyMapping("DCB", pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String locationCode) {
		if (isEmpty(fromContext)) {
			log.warn("Attempting to find mapping from location (code: \"{}\") to agency with empty from context", locationCode);

			return empty();
		}

		return referenceValueMappingService.findMapping("Location", fromContext,
			locationCode, "AGENCY", "DCB");
	}
}
