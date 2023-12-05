package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.model.Item;
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
public class SierraItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	/**
	 Status is interpreted based upon
	 <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
	 this documentation</a>
	 */
	public static ItemStatusMapper.FallbackMapper sierraItemStatusFallback() {
		return fallbackBasedUponAvailableStatuses("-");
	}

	SierraItemMapper(ItemStatusMapper itemStatusMapper, NumericItemTypeMapper itemTypeMapper,
		LocationToAgencyMappingService locationToAgencyMappingService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}

	public Mono<Item> mapResultToItem(SierraItem itemResult, String hostLmsCode, String localBibId) {
		log.debug("mapResultToItem({}, {}, {})", itemResult, hostLmsCode, localBibId);

		final var statusCode = getValue(itemResult.getStatus(), Status::getCode);
		final var dueDate = getValue(itemResult.getStatus(), Status::getDuedate);

		// Sierra item type comes from fixed field 61 - see https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
		// We need to be looking at getLocalItemTypeCode - getLocalItemType is giving us a human-readable string at the moment
		return itemStatusMapper.mapStatus(statusCode, dueDate, hostLmsCode, true, sierraItemStatusFallback())
			.map(itemStatus -> Item.builder()
				.localId(itemResult.getId())
				.status(itemStatus)
				.dueDate(parsedDueDate(itemResult))
				.location(org.olf.dcb.core.model.Location.builder()
					.code(itemResult.getLocation().getCode().trim())
					.name(itemResult.getLocation().getName())
					.build())
				.barcode(itemResult.getBarcode())
				.callNumber(itemResult.getCallNumber())
				.hostLmsCode(hostLmsCode)
				.holdCount(itemResult.getHoldCount())
				.localBibId(localBibId)
				.localItemType(itemResult.getItemType())
				.localItemTypeCode(determineLocalItemTypeCode(itemResult.getFixedFields()))
				.deleted(itemResult.getDeleted())
				.suppressed(itemResult.getSuppressed())
				.build())
			.flatMap(item -> itemTypeMapper.enrichItemWithMappedItemType(item, hostLmsCode))
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode));
	}

	@Nullable
	private Instant parsedDueDate(SierraItem result) {
		final var dueDate = result.getStatus().getDuedate();

		return isNotEmpty(dueDate)
			? Instant.parse(dueDate)
			: null;
	}

	private String determineLocalItemTypeCode(Map<Integer, FixedField> fixedFields) {
		// We should look into result.fixedFields for 61 here and set itemType according to that code
		// and not the human-readable text
		final var FIXED_FIELD_61 = 61;

		String localItemTypeCode = null;

		if (fixedFields != null) {
			if (fixedFields.get(FIXED_FIELD_61) != null) {
				localItemTypeCode = fixedFields.get(FIXED_FIELD_61).getValue().toString();
			}
		}

		return localItemTypeCode;
	}

	private String getValue(Status status, Function<Status, String> function) {
		return Optional.ofNullable(status)
			.map(function)
			.orElse(null);
	}
}
