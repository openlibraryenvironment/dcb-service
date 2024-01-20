package org.olf.dcb.core.interaction.polaris;

import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PolarisItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;

	private final LocationToAgencyMappingService locationToAgencyMappingService;

	public static ItemStatusMapper.FallbackMapper polarisFallback() {
		return fallbackBasedUponAvailableStatuses("In");
	}

	PolarisItemMapper(ItemStatusMapper itemStatusMapper, NumericItemTypeMapper itemTypeMapper,
		LocationToAgencyMappingService locationToAgencyMappingService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}

	public Mono<org.olf.dcb.core.model.Item> mapItemGetRowToItem(
		PAPIClient.ItemGetRow itemGetRow, String hostLmsCode, String localBibId) {

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
				.hostLmsCode(hostLmsCode)
				.localBibId(localBibId)
				.localItemType(itemGetRow.getMaterialType())
				.localItemTypeCode(itemGetRow.getMaterialTypeID())
				.suppressed(!itemGetRow.getIsDisplayInPAC())
				.deleted(false)
        .rawVolumeStatement(itemGetRow.getHoldingsStatement())
        .parsedVolumeStatement(parseVolumeStatement(itemGetRow.getHoldingsStatement()))
				.build())
				.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode))
				.flatMap(item -> itemTypeMapper.enrichItemWithMappedItemType(item, hostLmsCode));
	}

	private static Instant convertFrom(String dueDate) {
//		log.debug("Converting String: '{}', to class: '{}'", dueDate, ZonedDateTime.class);
		if (dueDate == null) {
			return null;
		}
		try {
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MMM[  ][ ]d yyyy hh:mma", Locale.ENGLISH);
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

  private String parseVolumeStatement(String volumeStatement) {

    // \b(?:Vol\.? ?|v)(\d+)(?:,? ?Item \d+)?\b
    // Explanation:
    //   \b: Word boundary to ensure that the pattern is matched as a whole word.
    //   (?:Vol\.? ?|v): Non-capturing group to match either "Vol", "Vol.", "v" or "v" followed by an optional period and space.
    //   (\d+): Capturing group to match one or more digits representing the volume.
    //   (?:,? ?Item \d+)?: Optional non-capturing group to match an optional comma, optional space, "Item", a space, and one or more digits. This is to handle cases like "Vol 1, Item 1".
    //   \b: Word boundary to complete the pattern matching.

    String result = null;
    if ( volumeStatement != null ) {
      result = volumeStatement;
    }
    return result;
  }

}
