package org.olf.dcb.core.interaction.polaris;

import static java.lang.Boolean.FALSE;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.rules.ObjectRuleset;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PolarisItemMapper {
	private final NumericItemTypeMapper itemTypeMapper;
	private final ConversionService conversionService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;

	PolarisItemMapper(NumericItemTypeMapper itemTypeMapper,
		ConversionService conversionService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		AgencyService agencyService) {

		this.itemTypeMapper = itemTypeMapper;
		this.conversionService = conversionService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
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
		PAPIClient.ItemGetRow itemGetRow, String hostLmsCode, String localBibId,
		@NonNull Optional<ObjectRuleset> itemSuppressionRules) {

		log.debug("map polaris item {} {} {}", itemGetRow, hostLmsCode, localBibId);

		return mapStatus(itemGetRow.getCircStatusName(), hostLmsCode)
			.map(status -> {
				final var localId = String.valueOf(itemGetRow.getItemRecordID());
				final var dueDate = convertFrom(itemGetRow.getDueDate());
				final var location = getLocation(itemGetRow);
				final var suppressionFlag = deriveItemSuppressedFlag(itemGetRow, itemSuppressionRules);
				final var parsedVolumeStatement = parseVolumeStatement(itemGetRow.getVolumeNumber());

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
					// API doesn't provide hold count, it is assumed that item's have no holds
					.holdCount(0)
					.build();
			})
			.flatMap(item -> enrichItemWithAgency(item, hostLmsCode))
			.flatMap(itemTypeMapper::enrichItemWithMappedItemType)
			.doOnSuccess(item -> log.debug("Mapped polaris item: {}", item));
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
	private Mono<Item> enrichItemWithAgency(Item item, String hostLmsCode) {
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

	private org.olf.dcb.core.model.Location getLocation(PAPIClient.ItemGetRow itemGetRow) {

		final var shelfLocation = getValueOrNull(itemGetRow, PAPIClient.ItemGetRow::getShelfLocation);

		if (shelfLocation == null) Location.builder().build();

		// Note: We get back the shelf location description from the API
		return org.olf.dcb.core.model.Location.builder()
			.code(shelfLocation)
			.name(shelfLocation)
			.build();
	}

	private boolean deriveItemSuppressedFlag(@NonNull PAPIClient.ItemGetRow itemGetRow,
		@NonNull Optional<ObjectRuleset> itemSuppressionRules) {

		// do we display in pac? if false we need true for suppression
		// if display in pac is set to true then suppression is false
		if ( Boolean.TRUE.equals(!itemGetRow.getIsDisplayInPAC()) ) {
			log.warn("POLARIS_ITEM_SUPPRESSION :: Item: {} Reason: {}",
				itemGetRow.getItemRecordID(), "isDisplayInPAC is false");
			return true;
		}

		// ObjectRuleset expects a map to evaluate?!
		final Map<String, Object> itemGetRowMap = convertToMapFrom(itemGetRow);

		// Grab the suppression rules set against the Host Lms
		// False is the default value for suppression if we can't find the named ruleset
		// or if there isn't one.
		return itemSuppressionRules
			.map( rules -> rules.negate().test(itemGetRowMap) ) // Negate as the rules evaluate "true" for inclusion
			.map(flag -> {
				if (flag) log.warn("POLARIS_ITEM_SUPPRESSION :: Item: {} Reason: {}",
					itemGetRow.getItemRecordID(), "ruleset condition match");
				return flag;
			})
			.orElse(FALSE);
	}

	private Map<String, Object> convertToMapFrom(PAPIClient.ItemGetRow itemGetRow) {
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

  private String parseVolumeStatement(String vol) {

		// In polaris, the volume statement can appear in the call number as Vol [ nn ] Pt [ nn ]
		// this regex "(Vol|Pt)\\s*\\[\\s*(\\d+)\\s*\\]" can extract those statements

    String result = null;
    if ( vol != null ) {

			// Extract Vol and Pt statements
			String regex = "(Vol|Pt)\\s*\\[\\s*(\\d+)\\s*\\]";
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(vol);
			while (matcher.find()) {
				String type = matcher.group(1);
				int number = Integer.parseInt(matcher.group(2));
				log.debug(type + ": " + number);
				if ( result == null )
					result = "";

				switch ( type ) {
					case "Vol":
						result = result + "v" + number;
						break;
					case "Pt":
						result = result + "p" + number;
						break;
				}
			}

			// Check for a 4 digit year at the end of the volume statement
			String year_regex = "\\d{4}$";
			Pattern p2 = Pattern.compile(year_regex);

      Matcher m2 = p2.matcher(vol);
			if ( m2.find() ) {
				if ( result == null )
				  result = "y"+m2.group();
				else
					result = result + "y" + m2.group();
			}
    }
    return result;
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
