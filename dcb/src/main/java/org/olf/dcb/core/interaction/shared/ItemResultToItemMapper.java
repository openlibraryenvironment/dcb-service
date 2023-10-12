package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isNotEmpty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

import org.olf.dcb.core.interaction.polaris.papi.PAPILmsClient;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

@Singleton
public class ItemResultToItemMapper {
	private static final Logger log = LoggerFactory.getLogger(ItemResultToItemMapper.class);
	private final ItemStatusMapper itemStatusMapper;
	private final ItemTypeMapper itemTypeMapper;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final AgencyRepository agencyRepository;
	private static final Integer FIXED_FIELD_61 = Integer.valueOf(61);

	ItemResultToItemMapper(ItemStatusMapper itemStatusMapper,
		ItemTypeMapper itemTypeMapper,
		ReferenceValueMappingRepository referenceValueMappingRepository,
		AgencyRepository agencyRepository)
	{
		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.agencyRepository = agencyRepository;
	}

	public Mono<org.olf.dcb.core.model.Item> mapResultToItem(SierraItem result, String hostLmsCode, String localBibId) {
		log.debug("mapResultToItem(result, {}, {})", hostLmsCode, localBibId);

		final var dueDate = result.getStatus().getDuedate();

		final var parsedDueDate = isNotEmpty(dueDate)
			? Instant.parse(dueDate)
			: null;

		final var localItemTypeCode = determineLocalItemTypeCode(result.getFixedFields());

		final var locationCode = result.getLocation().getCode().trim();

		return itemStatusMapper.mapStatus(result.getStatus(), hostLmsCode)
			.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
				.id(result.getId())
				.status(itemStatus)
				.dueDate(parsedDueDate)
				.location(org.olf.dcb.core.model.Location.builder()
					.code(locationCode)
					.name(result.getLocation().getName())
					.build())
				.barcode(result.getBarcode())
				.callNumber(result.getCallNumber())
				.hostLmsCode(hostLmsCode)
				.holdCount(result.getHoldCount())
				.localBibId(localBibId)
				.localItemType(result.getItemType())
				.localItemTypeCode(localItemTypeCode)
				.deleted(result.getDeleted())
				.suppressed(result.getSuppressed())
				.build())
				.flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode))
				.flatMap(item -> enrichItemAgencyFromShelvingLocation(item, hostLmsCode, locationCode));
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
		PAPILmsClient.ItemGetRow itemGetRow, String hostLmsCode, String localBibId) {

		return itemStatusMapper.mapStatus(Status.builder()
				.code(itemGetRow.getCircStatus())
				.duedate(itemGetRow.getDueDate())
				.build(), hostLmsCode)
			.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
				.id(String.valueOf(itemGetRow.getItemRecordID()))
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
				.localItemTypeCode(itemGetRow.getMaterialType())
				.suppressed(!itemGetRow.getIsDisplayInPAC())
				.build())
				.flatMap(item -> enrichItemAgencyFromShelvingLocation(item, hostLmsCode, item.getLocation().getCode().trim()))
				.flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode));
	}

	Mono<org.olf.dcb.core.model.Item> enrichItemAgencyFromShelvingLocation(org.olf.dcb.core.model.Item item, String hostSystem, String itemShelvingLocation) {
//			log.debug("map shelving location to agency  {}:\"{}\"",hostSystem,itemShelvingLocation);
			return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
				"ShelvingLocation", hostSystem, itemShelvingLocation, "AGENCY", "DCB"))
				.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode( rvm.getToValue() )))
				.map(dataAgency -> {
					item.setAgencyCode( dataAgency.getCode() );
					item.setAgencyDescription( dataAgency.getName() );
					return item;
				})
				.defaultIfEmpty(item);
	}

	Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item, String hostSystem) {
			// We need to be looking at getLocalItemTypeCode - getLocalItemType is giving us a human readable string at the moment
			// Sierra items should have a fixedField 61 according to https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
//		log.debug("enrichItemWithMappedItemType: hostSystem: {}, localItemTypeCode: {}", hostSystem, item.getLocalItemTypeCode());
			return itemTypeMapper.getCanonicalItemType(hostSystem, item.getLocalItemTypeCode())
							.defaultIfEmpty("UNKNOWN")
							.map( mappedType -> item.setCanonicalItemType(mappedType) );
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
