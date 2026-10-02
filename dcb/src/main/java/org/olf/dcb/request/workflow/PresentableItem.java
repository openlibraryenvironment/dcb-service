package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.CollectionUtils.mapList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Value
@Builder
public class PresentableItem {
	String localId;
	String barcode;
	String statusCode;
	Boolean requestable;
	String localItemType;
	String canonicalItemType;
	Integer holdCount;
	String agencyCode;
	String availableDate;
	String dueDate;
	String locationCode;
	String decisionLog;
	Map<String, String> rawDataValues;

	public static List<PresentableItem> toPresentableItems(List<Item> items) {
		return mapList(items, PresentableItem::toPresentableItem);
	}

	public static PresentableItem toPresentableItem(Item item) {
		if (item == null) {
			return null;
		}

		// For values that could be "unknown", "null" is used as a differentiating default
		return builder()
			.localId(getValue(item, Item::getLocalId, "Unknown"))
			.barcode(getValue(item, Item::getBarcode, "Unknown"))
			.statusCode(getStatusCode(item))
			.requestable(getValue(item, Item::getIsRequestable, false))
			.localItemType(getValue(item, Item::getLocalItemType, "null"))
			.canonicalItemType(getValue(item, Item::getCanonicalItemType, "null"))
			.holdCount(getValue(item, Item::getHoldCount, 0))
			.agencyCode(getValue(item, Item::getAgencyCode, "Unknown"))
			.availableDate(dateTimeToString(item, Item::getAvailableDate))
			.dueDate(dateTimeToString(item, Item::getDueDate))
			// The location is what a location-to-agency mapping is keyed on, and the decision log
			// is where an adapter says why it could not map an item: both are what an operator
			// needs when asking why this item was not chosen
			.locationCode(getValue(item, Item::getLocationCode, "null"))
			.decisionLog(decisionLog(item))
			.rawDataValues(getValueOrNull(item, Item::getRawDataValues))
			.build();
	}

	private static String decisionLog(Item item) {
		final var entries = getValueOrNull(item, Item::getDecisionLogEntries);

		return entries == null || entries.isEmpty() ? null : String.join("; ", entries);
	}

	private static String getStatusCode(Item item) {
		final var itemStatusCode = getValueOrNull(item, Item::getStatus, ItemStatus::getCode);

		return getValue(itemStatusCode, Enum::name, "null");
	}

	private static String dateTimeToString(Item item, Function<Item, Instant> getDateTime) {
		return getValue(item, getDateTime, Instant::toString, "null");
	}
}
