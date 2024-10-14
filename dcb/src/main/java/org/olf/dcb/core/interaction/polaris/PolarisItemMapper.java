package org.olf.dcb.core.interaction.polaris;

import static java.lang.Boolean.FALSE;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;

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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.json.tree.JsonNode;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.rules.ObjectRuleset;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PolarisItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;
	private final ConversionService conversionService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final AgencyService agencyService;

	public static ItemStatusMapper.FallbackMapper polarisFallback() {
		return fallbackBasedUponAvailableStatuses("In");
	}

	PolarisItemMapper(ItemStatusMapper itemStatusMapper, NumericItemTypeMapper itemTypeMapper,
										ConversionService conversionService, LocationToAgencyMappingService
											locationToAgencyMappingService, AgencyService agencyService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.conversionService = conversionService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.agencyService = agencyService;
	}

	public Mono<org.olf.dcb.core.model.Item> mapItemGetRowToItem(
		PAPIClient.ItemGetRow itemGetRow, String hostLmsCode, String localBibId,
		@NonNull Optional<ObjectRuleset> itemSuppressionRules) {

		log.debug("map polaris item {} {} {}",itemGetRow,hostLmsCode,localBibId);

		return itemStatusMapper.mapStatus(itemGetRow.getCircStatusName(),
				hostLmsCode, polarisFallback())
			.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
				.localId(String.valueOf(itemGetRow.getItemRecordID()))
				.status(itemStatus)
				.dueDate(convertFrom(itemGetRow.getDueDate()))
				.location(org.olf.dcb.core.model.Location.builder()
					.code(String.valueOf(itemGetRow.getLocationID()))
					.name(itemGetRow.getLocationName())
					.build())
				.barcode(itemGetRow.getBarcode())
				.callNumber(itemGetRow.getCallNumber())
				.localBibId(localBibId)
				.localItemType(itemGetRow.getMaterialType())
				.localItemTypeCode(itemGetRow.getMaterialTypeID())
				.suppressed(deriveItemSuppressedFlag(itemGetRow, itemSuppressionRules))
				.deleted(false)
        .rawVolumeStatement(itemGetRow.getVolumeNumber())
        .parsedVolumeStatement(parseVolumeStatement(itemGetRow.getVolumeNumber()))
				.build())
				.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode))
				.flatMap(itemTypeMapper::enrichItemWithMappedItemType);
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

}
