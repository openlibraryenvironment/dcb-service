package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;

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

	static List<PresentableItem> toPresentableItems(List<Item> items) {
		return items.stream()
			.map(PresentableItem::toPresentableItem)
			.collect(Collectors.toList());
	}

	static PresentableItem toPresentableItem(Item item) {
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
			.availableDate(
				Optional.ofNullable(getValue(item, Item::getAvailableDate, null))
				.map(Instant::toString).orElse("null"))
			.dueDate(Optional.ofNullable(getValue(item, Item::getDueDate, null))
				.map(Instant::toString).orElse("null"))
			.build();
	}

	private static String getStatusCode(Item item) {
		final var itemStatusCode = getValueOrNull(item, Item::getStatus, ItemStatus::getCode);
		return getValue(itemStatusCode, Enum::name, "null");
	}
}
