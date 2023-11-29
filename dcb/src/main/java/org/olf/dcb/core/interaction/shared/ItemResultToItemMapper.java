package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.polarisFallback;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.sierraFallback;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

import org.olf.dcb.core.interaction.polaris.PAPIClient;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

@Slf4j
@Singleton
public class ItemResultToItemMapper {
	private static final Integer FIXED_FIELD_61 = 61;
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;

	private final LocationToAgencyMappingService locationToAgencyMappingService;

	ItemResultToItemMapper(ItemStatusMapper itemStatusMapper,
		NumericItemTypeMapper itemTypeMapper,
		LocationToAgencyMappingService locationToAgencyMappingService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}

	public Mono<org.olf.dcb.core.model.Item> mapResultToItem(SierraItem itemResult,
		String hostLmsCode, String localBibId) {

		log.debug("mapResultToItem({}, {}, {})", itemResult, hostLmsCode, localBibId);

		final var localItemTypeCode = determineLocalItemTypeCode(itemResult.getFixedFields());

		final var locationCode = itemResult.getLocation().getCode().trim();

		return itemStatusMapper.mapStatus(itemResult.getStatus(), hostLmsCode, sierraFallback())
			.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
				.localId(itemResult.getId())
				.status(itemStatus)
				.dueDate(parsedDueDate(itemResult))
				.location(org.olf.dcb.core.model.Location.builder()
					.code(locationCode)
					.name(itemResult.getLocation().getName())
					.build())
				.barcode(itemResult.getBarcode())
				.callNumber(itemResult.getCallNumber())
				.hostLmsCode(hostLmsCode)
				.holdCount(itemResult.getHoldCount())
				.localBibId(localBibId)
				.localItemType(itemResult.getItemType())
				.localItemTypeCode(localItemTypeCode)
				.deleted(itemResult.getDeleted())
				.suppressed(itemResult.getSuppressed())
				.build())
			.flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode))
			.flatMap(item -> enrichItemAgencyFromShelvingLocation(item, hostLmsCode, locationCode));
	}

	@Nullable
	private static Instant parsedDueDate(SierraItem result) {
		final var dueDate = result.getStatus().getDuedate();

		return isNotEmpty(dueDate)
			? Instant.parse(dueDate)
			: null;
	}

	private static String determineLocalItemTypeCode(Map<Integer, FixedField> fixedFields) {
		// We should look into result.fixedFields for 61 here and set itemType according to that code
		// and not the human-readable text
		String localItemTypeCode = null;

		if (fixedFields != null) {
			if (fixedFields.get(FIXED_FIELD_61) != null) {
				localItemTypeCode = fixedFields.get(FIXED_FIELD_61).getValue().toString();
			}
		}

		return localItemTypeCode;
	}

	public Mono<org.olf.dcb.core.model.Item> mapItemGetRowToItem(
		PAPIClient.ItemGetRow itemGetRow, String hostLmsCode, String localBibId) {

		final var status = Status.builder()
			.code(itemGetRow.getCircStatusName())
			.duedate(itemGetRow.getDueDate())
			.build();

		return itemStatusMapper.mapStatus(status, hostLmsCode, polarisFallback())
			.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
				.localId(String.valueOf(itemGetRow.getItemRecordID()))
				.status(itemStatus)
				.dueDate( convertFrom(itemGetRow.getDueDate()) )
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
				.build())
				.flatMap(item -> enrichItemAgencyFromShelvingLocation(item, hostLmsCode, item.getLocation().getCode().trim()))
				.flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode));
	}

	Mono<org.olf.dcb.core.model.Item> enrichItemAgencyFromShelvingLocation(
		org.olf.dcb.core.model.Item item, String hostSystem, String itemShelvingLocation) {

		return locationToAgencyMappingService.mapLocationToAgency(hostSystem, itemShelvingLocation)
			.map(dataAgency -> {
				item.setAgencyCode(dataAgency.getCode());
				item.setAgencyDescription(dataAgency.getName());
				return item;
			})
			.defaultIfEmpty(item);
	}

	Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item, String hostSystem) {
			// We need to be looking at getLocalItemTypeCode - getLocalItemType is giving us a human readable string at the moment
			// Sierra items should have a fixedField 61 according to https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
			return itemTypeMapper.getCanonicalItemType(hostSystem, item.getLocalItemTypeCode())
				.defaultIfEmpty("UNKNOWN")
				.map(item::setCanonicalItemType);
	}

	public static Instant convertFrom(String dueDate) {
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
}
