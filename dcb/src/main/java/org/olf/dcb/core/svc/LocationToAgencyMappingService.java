package org.olf.dcb.core.svc;

import java.time.Instant;
import java.time.Duration;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.AlarmsService;
import services.k_int.utils.UUIDUtils;
import graphql.com.google.common.base.Predicates;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.justOrEmpty;
import static reactor.function.TupleUtils.function;

@Slf4j
@Singleton
public class LocationToAgencyMappingService {
	private static final String KEY_CONTEXT_HIERARCHY = "contextHierarchy";
	private final AgencyService agencyService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final HostLmsService hostLmsService;
	private final AlarmsService alarmsService;

	public LocationToAgencyMappingService(AgencyService agencyService,
		ReferenceValueMappingService referenceValueMappingService,
		HostLmsService hostLmsService,
    AlarmsService alarmsService) {

		this.agencyService = agencyService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.hostLmsService = hostLmsService;
		this.alarmsService = alarmsService;
	}

	/**
	 * Return an agency based on some external reference
	 */
	public Mono<DataAgency> dataAgencyFromMappedExernal(String fromContext, String fromCategory, String fromValue) {
		return mapExternalIdentifierToAgency(fromContext,fromCategory,fromValue);
	}

	private Mono<DataAgency> findLocationToAgencyMapping(Item item, String hostLmsCode) {

		final var locationCode = trimToNull(getValueOrNull(item, Item::getLocationCode));

		if (isEmpty(locationCode)) {
			return empty();
		}

		return mapExternalIdentifierToAgency(hostLmsCode, locationCode);
	}

	private Mono<DataAgency> mapExternalIdentifierToAgency(String hostLmsCode, String locationCode) {
		return mapExternalIdentifierToAgency(hostLmsCode, "Location", locationCode);
	}

	private Mono<DataAgency> mapExternalIdentifierToAgency(String hostLmsCode, String fromCategory, String locationCode) {

		return findLocationToAgencyMapping(hostLmsCode, fromCategory, locationCode)
			.map(ReferenceValueMapping::getToValue)
			.flatMap(agencyService::findByCode)
			.doOnNext(agency -> log.debug("Found agency for location: {}", agency))
      .switchIfEmpty(Mono.defer(() -> {
        log.warn("No agency found for locationCode={} (hostLmsCode={}, category={})", locationCode, hostLmsCode, fromCategory);
        String alarmCode = "ILS."+hostLmsCode+".LOCATION_TO_AGENCY_FAILURE."+fromCategory+"."+(locationCode.toString()).toUpperCase();
        // Alarm can last up to 5 days
        alarmsService.raise(Alarm.builder()
            .id(UUIDUtils.generateAlarmId(alarmCode))
            .code(alarmCode)
            .expires(Instant.now().plus(Duration.ofDays(5)))
            .build());
        return Mono.empty();
      }));
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String pickupLocationCode) {
		return findLocationToAgencyMapping("DCB", pickupLocationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String locationCode) {
		return findLocationToAgencyMapping(fromContext, "Location", locationCode);
	}

	public Mono<ReferenceValueMapping> findLocationToAgencyMapping(String fromContext, String fromCategory, String locationCode) {
		if (isEmpty(fromContext)) {
			log.warn("Attempting to find mapping from location (code: \"{}\") to agency with empty from context", locationCode);

			return empty();
		}

		if (isEmpty(locationCode)) {
			// This will happen for all FOLIO patrons so has lower log level than similar warning above
			log.debug("Attempting to find mapping from location to agency with empty code");

			return empty();
		}

		// Allow implementers to specify wildcards that will match all locations. Look for specific locations
		// before falling back to looking for any wildcards.
		List<String> lookupCodeList = List.of(locationCode, "*");

		return getContextHierarchyFor(fromContext)
			.flatMap(sourceContexts -> referenceValueMappingService.findMappingUsingHierarchyWithFallback(
				fromCategory, sourceContexts, lookupCodeList, "AGENCY", "DCB"));
	}
	
	public Mono<List<String>> getContextHierarchyFor(String context) {
		return getContextHierarchyFor( context, List.of(context));
	}

	/**
	 * A way to fetch a context hierarchy for a given context.
	 */
	public Mono<List<String>> getContextHierarchyFor(String context, List<String> defaults) {

		// guard clause for non-hostlms contexts
		if ("DCB".equals(context)) return Mono.justOrEmpty(defaults);

		return hostLmsService.getClientFor(context)
			// Keep non-null & non-empty lists
			.mapNotNull(hostLmsClient -> (List<String>) hostLmsClient.getConfig().get(KEY_CONTEXT_HIERARCHY))
			.filter(Predicates.not(List::isEmpty))
			// Fallback for non-null & non-empty lists
			.switchIfEmpty(Mono.defer(() -> {
				log.debug("[CONTEXT-HIERARCHY-EMPTY] " +
					"- Fetching 'contextHierarchy' returned an EMPTY list for context: '{}'", context);
				return Mono.justOrEmpty(defaults);
			}))
			// Fallback for error
			.onErrorResume(error -> {
				log.debug("[CONTEXT-HIERARCHY-ERROR] " +
					"- An ERROR occurred while fetching 'contextHierarchy' for context: '{}'.", context, error);
				return Mono.justOrEmpty(defaults);
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
			.map(Item::setOwningContext)
			.defaultIfEmpty(incomingItem);
	}
}
