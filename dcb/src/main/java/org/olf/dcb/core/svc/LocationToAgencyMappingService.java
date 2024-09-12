package org.olf.dcb.core.svc;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.justOrEmpty;
import static reactor.function.TupleUtils.function;

@Slf4j
@Singleton
public class LocationToAgencyMappingService {
	private final AgencyService agencyService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final HostLmsService hostLmsService;

	public LocationToAgencyMappingService(AgencyService agencyService,
		ReferenceValueMappingService referenceValueMappingService,
		HostLmsService hostLmsService) {

		this.agencyService = agencyService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.hostLmsService = hostLmsService;
	}


	private Mono<DataAgency> findLocationToAgencyMapping(Item item, String hostLmsCode) {
		final var locationCode = trimToNull(getValueOrNull(item, Item::getLocationCode));

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

		if (isEmpty(locationCode)) {
			// This will happen for all FOLIO patrons so has lower log level than similar warning above
			log.debug("Attempting to find mapping from location to agency with empty code");

			return empty();
		}

		return getContextHierarchyFor(fromContext)
			.flatMap(sourceContexts -> referenceValueMappingService.findMappingUsingHierarchy(
				"Location", sourceContexts, locationCode, "AGENCY", "DCB"));
	}

	/**
	 * A way to fetch a context hierarchy for a given context.
	 */
	private Mono<List<String>> getContextHierarchyFor(String context) {

		// guard clause for non-hostlms contexts
		if ("DCB".equals(context)) return Mono.just(List.of(context));

		return hostLmsService.getClientFor(context)
			.map(hostLmsClient -> (List<String>) hostLmsClient.getConfig().get("contextHierarchy"))
			// Keep non-null & non-empty lists
			.filter(list -> list != null && !list.isEmpty())
			// Fallback for non-null & non-empty lists
			.switchIfEmpty(Mono.defer(() -> {
				log.error("[CONTEXT-HIERARCHY-EMPTY] " +
					"- Fetching 'contextHierarchy' returned an EMPTY list for context: '{}'", context);
				return Mono.just(List.of(context));
			}))
			// Fallback for error
			.onErrorResume(error -> {
				log.error("[CONTEXT-HIERARCHY-ERROR] " +
					"- An ERROR occurred while fetching 'contextHierarchy' for context: '{}'.", context, error);
				return Mono.just(List.of(context));
			});
	}

	public Mono<String> findDefaultAgencyCode(String hostLmsCode) {
		log.debug("Attempting to use default agency for Host LMS: {}", hostLmsCode);

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> justOrEmpty(getValueOrNull(client, HostLmsClient::getDefaultAgencyCode)))
			.doOnSuccess(defaultAgencyCode -> log.debug(
				"Found default agency code {} for Host LMS {}", defaultAgencyCode, hostLmsCode))
			.doOnError(error -> log.error(
				"Error occurred getting default agency code for Host LMS {}", hostLmsCode, error));
	}

	public Mono<Item> enrichItemAgencyFromLocation(Item incomingItem, String hostLmsCode) {
		return Mono.just(incomingItem)
			.zipWhen(item -> findLocationToAgencyMapping(item, hostLmsCode))
			.map(function(Item::setAgency))
			.map(setOwningContext())
			.defaultIfEmpty(incomingItem);
	}


	private Function<Item, Item> setOwningContext() {
		return item -> {

			// fetch hostlmscode from the items agency
			final var hostLmsCode = getValueOrNull(item, Item::getHostLmsCode);

			if (hostLmsCode == null) {
				log.error("Could not add the owningConext as getHostLmsCode was null");
				return item;
			}

			return item.setOwningContext(hostLmsCode);
		};
	}
}
