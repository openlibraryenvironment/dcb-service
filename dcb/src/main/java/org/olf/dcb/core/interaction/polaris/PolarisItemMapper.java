package org.olf.dcb.core.interaction.polaris;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.model.DerivedLoanPolicy.GENERAL;
import static org.olf.dcb.core.model.DerivedLoanPolicy.LOCAL_ONLY;
import static org.olf.dcb.core.model.DerivedLoanPolicy.NO_LEND;
import static org.olf.dcb.core.model.DerivedLoanPolicy.REFERENCE_ONLY;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.interaction.polaris.PAPIClient.ItemGetRow;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.DerivedLoanPolicy;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.VolumeDesignation;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.rules.AnnotatedObject;
import org.olf.dcb.rules.ObjectRuleset;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Slf4j
@Singleton
public class PolarisItemMapper {
	private final NumericItemTypeMapper itemTypeMapper;
	private final ConversionService conversionService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;
	private final AlarmsService alarmsService;
	private final MeterRegistry meterRegistry;

	static final String NOT_LENT_METRIC = "dcb.polaris.items.not.lent";

	PolarisItemMapper(NumericItemTypeMapper itemTypeMapper,
		ConversionService conversionService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService,
		AlarmsService alarmsService,
		MeterRegistry meterRegistry) {

		this.itemTypeMapper = itemTypeMapper;
		this.conversionService = conversionService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
		this.alarmsService = alarmsService;
		this.meterRegistry = meterRegistry;
	}

	// Tags from fixed vocabularies: the Host LMS codes configured here, the policy enum, and the four reasons
	private void countNotLent(String hostLmsCode, DerivedLoanPolicy policy, List<String> decisionLogEntries) {
		if (policy != NO_LEND && policy != LOCAL_ONLY && policy != REFERENCE_ONLY) {
			return;
		}

		final var decision = decisionLogEntries.stream()
			.filter(entry -> entry.startsWith("LoanPolicy "))
			.reduce((first, last) -> last)
			.orElse("");

		final var reason = decision.contains("not holdable") ? "not_holdable"
			: decision.contains("non-circulating") ? "non_circulating"
			: decision.contains("not loanable outside the system") ? "not_loanable_outside_system"
			: "shelf_location_policy";

		meterRegistry.counter(NOT_LENT_METRIC, "lms", String.valueOf(hostLmsCode),
			"policy", policy.name(), "reason", reason).increment();
	}

	/**
	 * Maps a Polaris item to a DCB Item.
	 *
	 * @param itemGetRow item from the polaris API to map
	 * @param hostLmsCode the host LMS code
	 * @param localBibId the local bib ID
	 * @param itemSuppressionRules the item suppression rules
	 * @return a Mono containing a fully mapped DCB Item
	 */
	public Mono<org.olf.dcb.core.model.Item> mapItemGetRowToItem(
		ItemGetRow itemGetRow, String hostLmsCode, String localBibId,
		@NonNull Optional<ObjectRuleset> itemSuppressionRules, @NonNull PolarisConfig polarisConfig) {

		final String itemAgencyResolutionMethod = polarisConfig.getItemAgencyResolutionMethod();

		log.debug("map polaris item {} {} {} {}", itemGetRow, hostLmsCode, localBibId, itemAgencyResolutionMethod);

		List<String> decisionLogEntries = new ArrayList<String>();

		return mapStatus(itemGetRow.getCircStatusName(), hostLmsCode)
			.map(status -> {
				final var localId = String.valueOf(itemGetRow.getItemRecordID());
				final var dueDate = convertFrom(itemGetRow.getDueDate());
				final var location = getLocation(itemGetRow, itemAgencyResolutionMethod);
				final var shelvingLocation = itemGetRow.getShelfLocation();
				final var suppressionFlag = deriveItemSuppressedFlag(itemGetRow, itemSuppressionRules, decisionLogEntries);
				final var parsedVolumeStatement = VolumeDesignation.normalise(itemGetRow.getVolumeNumber());

				final var derivedLoanPolicy = isNeverLent(itemGetRow, decisionLogEntries)
					? NO_LEND
					: keptInsideTheSystem(itemGetRow, deriveLoanPolicy(hostLmsCode, shelvingLocation,
						polarisConfig.getShelfLocationPolicyMap(), decisionLogEntries), decisionLogEntries);

				countNotLent(hostLmsCode, derivedLoanPolicy, decisionLogEntries);

				return org.olf.dcb.core.model.Item.builder()
					.localId(localId)
					.status(status)
					.dueDate(dueDate)
					.location(location)
					.barcode(itemGetRow.getBarcode())
					.callNumber(itemGetRow.getCallNumber())
					.localBibId(localBibId)
					.localItemType(itemGetRow.getMaterialType())
					.localItemTypeCode(itemGetRow.getMaterialTypeID())
					.suppressed(suppressionFlag)
					.deleted(false)
					.rawVolumeStatement(itemGetRow.getVolumeNumber())
					.parsedVolumeStatement(parsedVolumeStatement)
					.sourceHostLmsCode(hostLmsCode)
					.owningContext(hostLmsCode)
					.derivedLoanPolicy(derivedLoanPolicy)
					.shelvingLocation(shelvingLocation)
					// API doesn't provide hold count, it is assumed that item's have no holds
					.holdCount(0)
					.rawDataValue("circStatusName",itemGetRow.getCircStatusName())
					.rawDataValue("locationID",""+itemGetRow.getLocationID())
					.rawDataValue("collectionID",""+itemGetRow.getCollectionID())
					.rawDataValue("shelfLocation",itemGetRow.getShelfLocation())
					.decisionLogEntries(decisionLogEntries)
					.build();
			})
			.flatMap(item -> enrichItemWithAgency(itemGetRow, item, hostLmsCode, itemAgencyResolutionMethod))
			.flatMap(itemTypeMapper::enrichItemWithMappedItemType)
			.doOnSuccess(item -> log.debug("Mapped polaris item: {}", item));
	}

	// Either flag wins over the shelf location policy map. An absent flag is not a refusal.
	private static boolean isNeverLent(ItemGetRow itemGetRow, List<String> decisionLogEntries) {
		if (FALSE.equals(itemGetRow.getHoldable())) {
			decisionLogEntries.add("LoanPolicy NO_LEND because the item is not holdable");
			return true;
		}

		if (TRUE.equals(itemGetRow.getNonCirculating())) {
			decisionLogEntries.add("LoanPolicy NO_LEND because the item is non-circulating");
			return true;
		}

		return false;
	}

	// Not loanable outside the system is lent to the system's own patrons only. A stricter policy from
	// the shelf location map stands; an absent flag changes nothing
	private static DerivedLoanPolicy keptInsideTheSystem(ItemGetRow itemGetRow, DerivedLoanPolicy policy,
		List<String> decisionLogEntries) {

		if (!FALSE.equals(itemGetRow.getLoanableOutsideSystem())
			|| policy == NO_LEND || policy == REFERENCE_ONLY || policy == LOCAL_ONLY) {
			return policy;
		}

		decisionLogEntries.add("LoanPolicy LOCAL_ONLY because the item is not loanable outside the system");
		return LOCAL_ONLY;
	}

	private DerivedLoanPolicy deriveLoanPolicy(String hostLmsCode, String shelvingLocation,
		Map<String,String> shelfLocationPolicyMap, List<String> decisionLogEntries) {

		// If anything is missing, default to GENERAL
		if ( ( shelvingLocation == null ) || 
		     ( shelfLocationPolicyMap == null ) ||
         ( ! shelfLocationPolicyMap.containsKey(shelvingLocation) ) )
		{
			decisionLogEntries.add("LoanPolicy GENERAL because shelvingLocation null OR shelfLocationPolicyMap null OR "+shelfLocationPolicyMap+" contains key "+shelvingLocation);
			return GENERAL;
		}

		final var configuredPolicy = shelfLocationPolicyMap.get(shelvingLocation);
		try {
			final var policy = DerivedLoanPolicy.valueOf(configuredPolicy);
			decisionLogEntries.add("LoanPolicy derived by taking value of " + shelvingLocation
				+ " for shelving location");
			return policy;
		} catch (IllegalArgumentException | NullPointerException error) {
			log.warn("Invalid Polaris shelf location policy [{}] for Host LMS [{}], shelf location [{}]; using UNKNOWN",
				configuredPolicy, hostLmsCode, shelvingLocation);
			decisionLogEntries.add("LoanPolicy UNKNOWN because configured policy " + configuredPolicy
				+ " for shelving location " + shelvingLocation + " is invalid");
			raiseInvalidShelfLocationPolicyAlarm(hostLmsCode, shelvingLocation, configuredPolicy);
			return DerivedLoanPolicy.UNKNOWN;
		}
	}

	private void raiseInvalidShelfLocationPolicyAlarm(String hostLmsCode, String shelvingLocation,
		String configuredPolicy) {

		final var alarmCode = "ILS." + hostLmsCode + ".POLARIS_INVALID_SHELF_LOCATION_POLICY";
		alarmsService.raiseAccumulating(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.expires(Instant.now().plus(Duration.ofDays(5)))
				.build(),
			"invalidShelfLocationPolicies", shelvingLocation + "=" + configuredPolicy)
			.subscribe(
				ignored -> { },
				error -> log.warn("Unable to record invalid Polaris shelf location policy against {}",
					alarmCode, error));
	}

	/**
	 * Enriches the item with the agency mapped from the location code,
	 * using a fallback default agency if no location code is found.
	 * If no agency is found from a mapping then no default agency is used.
	 *
	 * @param item the item to enrich
	 * @param hostLmsCode the host LMS code
	 * @return a Mono containing the enriched item
	 */
	private Mono<Item> enrichItemWithAgency(ItemGetRow itemGetRow, Item item, String hostLmsCode, String itemAgencyResolutionMethod) {
		return switch(itemAgencyResolutionMethod) {
			case "LocationId" -> enrichItemWithAgencyUsingLocationId(itemGetRow, item, hostLmsCode);
			default -> legacyEnrichItemWithAgency(item, hostLmsCode);
    };
  }

	private Mono<Item> enrichItemWithAgencyUsingLocationId(ItemGetRow itemGetRow, Item item, String hostLmsCode) {
		return locationToAgencyMappingService.dataAgencyFromMappedExternal(hostLmsCode, "Location", itemGetRow.getLocationID().toString())
			.map(item::setAgency)
			.switchIfEmpty(Mono.defer(() -> {
				return Mono.just(item);
			}));
	}
 
	private Mono<Item> legacyEnrichItemWithAgency(Item item, String hostLmsCode) {
		return Optional.ofNullable(getValueOrNull(item, Item::getLocationCode))
			.map(locationCode -> useLocationMappingToFindAgency(item, hostLmsCode))
			.orElseGet(() -> useDefaultAgency(item, hostLmsCode));
	}

	private Mono<Item> useLocationMappingToFindAgency(Item item, String hostLmsCode) {
		return locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode)
			.switchIfEmpty(Mono.defer(() -> {

				log.warn("No agency found for shelving location description: {}", item.getLocationCode());

				return Mono.just(item);
			}));
	}

	public Mono<Item> useDefaultAgency(Item item, String hostLmsCode) {
		return locationToAgencyMappingService.findDefaultAgencyCode(hostLmsCode)
			.flatMap(agencyService::findByCode)
			.map(item::setAgency)
			.map(Item::setOwningContext)
			.switchIfEmpty(Mono.defer(() -> {

				log.warn("No default agency found for Host LMS: {}", hostLmsCode);

				return Mono.just(item);
			}));
	}

	private org.olf.dcb.core.model.Location getLocation(ItemGetRow itemGetRow, String itemAgencyResolutionMethod) {

		final var locationName = "LocationId".equals(itemAgencyResolutionMethod)
			? getValueOrNull(itemGetRow, ItemGetRow::getLocationName)
			: getValueOrNull(itemGetRow, ItemGetRow::getShelfLocation);

		if (locationName == null) return Location.builder().build();

		// Note: We get back the shelf location description from the API
		return org.olf.dcb.core.model.Location.builder()
			.code(itemGetRow.getLocationID() != null ? itemGetRow.getLocationID().toString() : locationName)
			.name(locationName)
			.build();
	}

	private boolean deriveItemSuppressedFlag(ItemGetRow itemGetRow,
		Optional<ObjectRuleset> itemSuppressionRules, List<String> decisionLog) {

		// do we display in pac? if false we need true for suppression
		// if display in pac is set to true then suppression is false
		final var displayInPAC = getValue(itemGetRow, ItemGetRow::getIsDisplayInPAC, false);

		if (!displayInPAC) {
			log.warn("POLARIS_ITEM_SUPPRESSION :: Item: {} Reason: {}", itemGetRow.getItemRecordID(), "isDisplayInPAC is false");
			decisionLog.add("suppress because isDisplayInPAC is FALSE");
			return true;
		}

		// ObjectRuleset expects a map to evaluate?!
		final Map<String, Object> itemGetRowMap = convertToMapFrom(itemGetRow);

		// Grab the suppression rules set against the Host Lms
		// False is the default value for suppression if we can't find the named ruleset
		// or if there isn't one.
		return itemSuppressionRules
      .map( rules -> rules.negate().test(new AnnotatedObject(itemGetRowMap, decisionLog)) ) // Negate as the rules evaluate "true" for inclusion
      .map(flag -> {
        if (flag) 
					log.warn("POLARIS_ITEM_SUPPRESSION :: Item: {} Reason: {}", itemGetRow.getItemRecordID(), "ruleset condition match");
				return flag;
			})
			.orElse(FALSE);
	}

	private Map<String, Object> convertToMapFrom(ItemGetRow itemGetRow) {
		final var rawJson = conversionService.convertRequired(itemGetRow, JsonNode.class);
		@SuppressWarnings("unchecked")
		final Map<String, Object> itemGetRowMap = conversionService.convertRequired(rawJson, Map.class);
		return itemGetRowMap;
	}

	private static Instant convertFrom(String dueDate) {
//		log.debug("Converting String: '{}', to class: '{}'", dueDate, ZonedDateTime.class);
		if (dueDate == null) {
			return null;
		}
		try {
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MMM[ ][ ][d] yyyy [ ]h:[ ]ma", Locale.ENGLISH);
			LocalDateTime localDateTime = LocalDateTime.parse(dueDate, inputFormatter);
			ZoneId zoneId = ZoneId.of("UTC");

			return localDateTime.atZone(zoneId).toInstant();
		} catch (DateTimeParseException e) {
			log.error("Failed to parse due date: {}", dueDate);
			log.error("Error details:", e);
			return null;
		} catch (Exception e) {
			log.error("An unexpected error occurred while parsing due date: {}", dueDate);
			log.error("Error details:", e);
			return null;
		}
	}

	public Mono<ItemStatus> mapStatus(String statusCode, String hostLmsCode) {
		log.debug("mapStatus(statusCode: {}, hostLmsCode: {})", statusCode, hostLmsCode);

		if (statusCode == null || (statusCode.isEmpty())) {
			return Mono.just(new ItemStatus(UNKNOWN));
		}

		return Mono.just( mapStatusCode(statusCode) )
			.map(ItemStatus::new);
	}

	/**
	 * The code used came from the descriptions from
	 * <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/itemstatuses/get_item_statuses">this API</a>
	 */
	private ItemStatusCode mapStatusCode(String statusCode) {
		return switch (statusCode) {
			case "In" -> AVAILABLE;
			case "Out" -> CHECKED_OUT;
			default -> UNAVAILABLE;
		};
	}
}
