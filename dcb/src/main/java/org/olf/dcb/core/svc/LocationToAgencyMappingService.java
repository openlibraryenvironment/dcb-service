package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;
import static services.k_int.utils.ReactorUtils.consumeOnSuccess;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.AgencyRepository;

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
		return findLocationToAgencyMapping(hostLmsCode, locationCode)
			.flatMap(mapping -> Mono.from(agencyRepository.findOneByCode(mapping.getToValue())))
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

	private Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(
		String pickupLocationContext, String pickupLocationCode) {

		return findLocationToAgencyMapping(pickupLocationContext, pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findPickupLocationToAgencyMapping(
		String pickupLocationCode, String pickupLocationContext, String requestorLocalSystemCode) {

		return findLocationToAgencyMapping(pickupLocationCode)
			.switchIfEmpty(Mono.defer(() -> findPickupLocationToAgencyMapping(pickupLocationContext, pickupLocationCode)))
			.switchIfEmpty(Mono.defer(() -> findPickupLocationToAgencyMapping(requestorLocalSystemCode, pickupLocationCode)))
			.doOnSuccess(consumeOnSuccess(
				() -> log.info("No pickup location mapping found for {} {} {}",pickupLocationCode,pickupLocationContext,requestorLocalSystemCode),
				mapping -> log.debug("Found mapping: {}", mapping)));
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String pickupLocationCode) {
		return findLocationToAgencyMapping("DCB", pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String locationCode) {
		if (isEmpty(fromContext)) {
			log.warn("Attempting to find mapping from location (code: \"{}\") to agency with empty from context", locationCode);

			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("Location", fromContext,
			locationCode, "AGENCY", "DCB");
	}
}
