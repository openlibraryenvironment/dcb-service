package org.olf.dcb.core.svc;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.trimToNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.core.publisher.Mono.empty;
import static reactor.core.publisher.Mono.justOrEmpty;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.request.workflow.exceptions.UnableToResolveAgencyProblem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Singleton
public class LocationToAgencyMappingService {
	private final AgencyService agencyService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final HostLmsService hostLmsService;
	private final HostLmsContextService hostLmsContextService;
	private final AlarmsService alarmsService;

	public LocationToAgencyMappingService(AgencyService agencyService,
		ReferenceValueMappingService referenceValueMappingService,
		HostLmsService hostLmsService,
		HostLmsContextService hostLmsContextService,
    AlarmsService alarmsService) {

		this.agencyService = agencyService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.hostLmsService = hostLmsService;
		this.hostLmsContextService = hostLmsContextService;
		this.alarmsService = alarmsService;
	}

	/**
	 * Return an agency based on some external reference
	 */
	public Mono<DataAgency> dataAgencyFromMappedExternal(String fromContext,
		String fromCategory, String fromValue) {

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

	private Mono<DataAgency> mapExternalIdentifierToAgency(String hostLmsCode,
		String fromCategory, String locationCode) {

		return findLocationToAgencyMapping(hostLmsCode, fromCategory, locationCode)
			.map(ReferenceValueMapping::getToValue)
			.flatMap(agencyService::findByCode)
			.doOnNext(agency -> log.debug("Found agency for location: {}", agency))
			.switchIfEmpty(Mono.defer(() -> {
				log.warn("No agency found for locationCode={} (hostLmsCode={}, category={})",
					locationCode, hostLmsCode, fromCategory);

				raiseUnmappedLocationAlarm(hostLmsCode, fromCategory, locationCode);

				return Mono.empty();
			}));
	}

	/**
	 * One alarm per Host LMS and category, listing the location codes that could not
	 * be mapped.
	 * <p>
	 * This used to build an alarm code per location, which would announce every
	 * unmapped code separately - and bringing a shared system with sixty branches
	 * online is exactly when an operator least wants sixty notifications. It also
	 * never subscribed to the result, so no alarm was ever actually raised.
	 */
	private void raiseUnmappedLocationAlarm(String hostLmsCode, String fromCategory,
		String locationCode) {

		final var alarmCode = "ILS." + hostLmsCode + ".LOCATION_TO_AGENCY_FAILURE." + fromCategory;

		// Deliberately detached rather than composed into the caller: this sits on the
		// per-item availability path, and an operator-facing notice is not worth making
		// item mapping wait on a write. Safe to fire concurrently - the accumulation
		// is a single atomic statement, not a read-modify-write.
		alarmsService.raiseAccumulating(Alarm.builder()
					.id(UUIDUtils.generateAlarmId(alarmCode))
					.code(alarmCode)
					// Alarm can last up to 5 days
					.expires(Instant.now().plus(Duration.ofDays(5)))
					.build(),
				"unmappedLocationCodes", locationCode.toUpperCase())
			.subscribe(
				ignored -> { },
				error -> log.warn("Unable to record unmapped location {} against {}",
					locationCode, alarmCode, error));
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

		return hostLmsContextService.forContext(fromContext)
			.flatMap(mappingContext -> referenceValueMappingService.findMappingUsingHierarchyWithFallback(
				fromCategory, mappingContext.sourceContexts(),
				lookupCodesFor(locationCode, mappingContext.sharedSystem()), "AGENCY", "DCB"));
	}

	/**
	 * Which location codes to try, in order.
	 * <p>
	 * Implementers can specify a wildcard matching every location, so look for the
	 * specific code before falling back to it.
	 * <p>
	 * A wildcard says "every location on this system belongs to one agency", which is
	 * exactly wrong on a shared system - it sweeps every co-tenant library, including
	 * ones not in the consortium at all, onto whichever agency happened to be
	 * configured. A shared system must map each location explicitly.
	 */
	private static List<String> lookupCodesFor(String locationCode, boolean sharedSystem) {
		return sharedSystem
			? List.of(locationCode)
			: List.of(locationCode, "*");
	}

	/**
	 * Resolve the agency a patron belongs to from their home library code.
	 * <p>
	 * This is the single answer to "which library is this patron from". Preflight and
	 * the patron validation transition both ask it, and they must agree: a request
	 * accepted at preflight against one agency and then validated against another is
	 * worse than a request refused outright. Anything with its own copy of this logic
	 * will drift - the previous duplicate in ValidatePatronTransition skipped both the
	 * context hierarchy and the wildcard fallback, so on any shared or hierarchical
	 * configuration the two stages could disagree.
	 *
	 * @return the resolved agency, or {@link UnableToResolveAgencyProblem} if neither
	 * the home library code nor the Host LMS default resolves to one
	 */
	public Mono<DataAgency> resolveAgencyForPatronHomeLocation(String hostLmsCode,
		String homeLibraryCode) {

		if (isEmpty(hostLmsCode)) {
			return Mono.error(new IllegalArgumentException(
				"Missing system code. Unable to resolve an agency for the patron"));
		}

		log.debug("resolveAgencyForPatronHomeLocation({}, {})", hostLmsCode, homeLibraryCode);

		return findLocationToAgencyMapping(hostLmsCode, homeLibraryCode)
			.map(ReferenceValueMapping::getToValue)
			// findDefaultAgencyCode is a no-op on a shared system, where no single
			// agency can stand in for an unrecognised location
			.switchIfEmpty(Mono.defer(() -> findDefaultAgencyCode(hostLmsCode)))
			.flatMap(agencyService::findByCode)
			.doOnNext(agency -> log.debug("Resolved patron home library {}/{} to agency {}",
				hostLmsCode, homeLibraryCode, agency.getCode()))
			.switchIfEmpty(UnableToResolveAgencyProblem.raiseError(homeLibraryCode, hostLmsCode));
	}

	/**
	 * The agency to assume when a patron's home location does not map to one.
	 * <p>
	 * Suppressed on a shared system, which is the whole point of the flag: standing
	 * one agency in for an unrecognised location there attributes every co-tenant
	 * library's patrons - including libraries outside the consortium entirely - to
	 * whichever agency happened to be configured, with nothing to say it happened.
	 * <p>
	 * The guard is here rather than on {@link HostLmsClient#getDefaultAgencyCode()}
	 * because this fallback is the only meaning a shared system invalidates. The
	 * appliance reads the same config key as its NCIP identity and still needs it.
	 */
	public Mono<String> findDefaultAgencyCode(String hostLmsCode) {
		log.debug("Attempting to use default agency for Host LMS: {}", hostLmsCode);

		return hostLmsService.getClientFor(hostLmsCode)
			.filter(client -> !client.isSharedSystem())
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
