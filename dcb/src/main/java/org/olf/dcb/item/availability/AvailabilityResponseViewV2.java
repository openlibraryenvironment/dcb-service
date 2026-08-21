package org.olf.dcb.item.availability;

import static org.olf.dcb.utils.CollectionUtils.mapList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.AvailabilityReason;
import org.olf.dcb.core.model.Item;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Serdeable
public class AvailabilityResponseViewV2 {
	private final List<ItemView> itemList;
	private final List<AvailabilityResponseView.Error> errors;
	private final Map<String, Long> timings;
	private final UUID clusteredBibId;

	public static AvailabilityResponseViewV2 from(AvailabilityReport report, UUID clusteredBibId) {
		final var timings = new LinkedHashMap<String, Long>();
		report.getTimings().forEach(tuple -> timings.put(tuple.getT1(), tuple.getT2()));

		return new AvailabilityResponseViewV2(
			mapList(getValueOrNull(report, AvailabilityReport::getItems), AvailabilityResponseViewV2::mapItem),
			mapList(getValueOrNull(report, AvailabilityReport::getErrors), AvailabilityResponseView::mapError),
			timings,
			clusteredBibId);
	}

	private static ItemView mapItem(Item item) {
		return ItemView.builder()
			.id(getValueOrNull(item, Item::getLocalId))
			.status(AvailabilityResponseView.mapStatus(item))
			.dueDate(getValueOrNull(item, Item::getDueDate))
			.location(AvailabilityResponseView.mapLocation(item))
			.barcode(getValueOrNull(item, Item::getBarcode))
			.callNumber(getValueOrNull(item, Item::getCallNumber))
			.hostLmsCode(getValueOrNull(item, Item::getHostLmsCode))
			.sourceHostLmsCode(getValueOrNull(item, Item::getSourceHostLmsCode))
			.owningContext(getValueOrNull(item, Item::getOwningContext))
			.isRequestable(getValueOrNull(item, Item::getIsRequestable))
			.isSuppressed(getValueOrNull(item, Item::getSuppressed))
			.holdCount(getValueOrNull(item, Item::getHoldCount))
			.availabilityDate(getValueOrNull(item, Item::getAvailableDate))
			.localItemType(getValueOrNull(item, Item::getLocalItemType))
			.canonicalItemType(getValueOrNull(item, Item::getCanonicalItemType))
			.localItemTypeCode(getValueOrNull(item, Item::getLocalItemTypeCode))
			.agency(AvailabilityResponseView.mapAgency(item))
			.rawVolumeStatement(getValueOrNull(item, Item::getRawVolumeStatement))
			.parsedVolumeStatement(getValueOrNull(item, Item::getParsedVolumeStatement))
			.rawDataValues(getValueOrNull(item, Item::getRawDataValues))
			.decisionLogEntries(getValueOrNull(item, Item::getDecisionLogEntries))
			.statusCorrectAsOf(getValueOrNull(item, Item::getStatusCorrectAsOf))
			.itemAccessType(getValueOrNull(item, Item::getItemAccessType))
			.electronicResourceUrl(getValueOrNull(item, Item::getElectronicResourceUrl))
			.availabilityReason(getValueOrNull(item, Item::getAvailabilityReason))
			.build();
	}

	@Value
	@Builder
	@Serdeable
	public static class ItemView {
		String id;
		AvailabilityResponseView.Status status;
		@Nullable Instant dueDate;
		AvailabilityResponseView.Location location;
		String barcode;
		String callNumber;
		@Nullable String hostLmsCode;
		@Nullable String sourceHostLmsCode;
		String owningContext;
		Boolean isRequestable;
		@Nullable Boolean isSuppressed;
		Integer holdCount;
		Instant availabilityDate;
		String localItemType;
		String canonicalItemType;
		String localItemTypeCode;
		AvailabilityResponseView.Agency agency;
		@Nullable String rawVolumeStatement;
		@Nullable String parsedVolumeStatement;
		Map<String, String> rawDataValues;
		List<String> decisionLogEntries;
		Instant statusCorrectAsOf;
		String itemAccessType;
		@Nullable String electronicResourceUrl;
		@Nullable AvailabilityReason availabilityReason;
	}
}
