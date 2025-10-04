package org.olf.dcb.item.availability;

import static org.olf.dcb.utils.CollectionUtils.mapList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Serdeable
public class AvailabilityResponseView {
	private final List<ARVItem> itemList;
	private final List<Error> errors;
	private final Map<String, Long> timings;
	private final UUID clusteredBibId;

	public static AvailabilityResponseView from(AvailabilityReport report,
		UUID clusteredBibId) {

		final var items = getValueOrNull(report, AvailabilityReport::getItems);

		final var mappedItems = mapList(items, AvailabilityResponseView::mapItem);

		final var errors = getValueOrNull(report, AvailabilityReport::getErrors);

		final var mappedErrors = mapList(errors, AvailabilityResponseView::mapError);
		
		final var timingsMap = new LinkedHashMap<String, Long> ();
			report.getTimings().forEach( tuple -> timingsMap.put(tuple.getT1(), tuple.getT2()) );
				
		return new AvailabilityResponseView(mappedItems, mappedErrors, timingsMap, clusteredBibId);
	}

	private static ARVItem mapItem(Item item) {
		return ARVItem.builder()
			.id(getValueOrNull(item, Item::getLocalId))
			.barcode(getValueOrNull(item, Item::getBarcode))
			.status(mapStatus(item))
			.dueDate(getValueOrNull(item, Item::getDueDate))
			.holdCount(getValueOrNull(item, Item::getHoldCount))
			.callNumber(getValueOrNull(item, Item::getCallNumber))
			.localItemType(getValueOrNull(item, Item::getLocalItemType))
			.localItemTypeCode(getValueOrNull(item, Item::getLocalItemTypeCode))
			.canonicalItemType(getValueOrNull(item, Item::getCanonicalItemType))
			.location(mapLocation(item))
			.isSuppressed(getValueOrNull(item, Item::getSuppressed))
			.rawVolumeStatement(getValueOrNull(item, Item::getRawVolumeStatement))
			.parsedVolumeStatement(getValueOrNull(item, Item::getParsedVolumeStatement))
			.agency(mapAgency(item))
			.hostLmsCode(getValueOrNull(item, Item::getHostLmsCode))
			.sourceHostLmsCode(getValueOrNull(item, Item::getSourceHostLmsCode))
			.availabilityDate(getValueOrNull(item, Item::getAvailableDate))
			.isRequestable(getValueOrNull(item, Item::getIsRequestable))
			.owningContext(getValueOrNull(item, Item::getOwningContext))
			.rawDataValues(getValueOrNull(item, Item::getRawDataValues))
			.decisionLogEntries(getValueOrNull(item, Item::getDecisionLogEntries))
			.build();
	}

	private static Location mapLocation(Item item) {
		return Location.builder()
			.code(getValueOrNull(item, Item::getLocation, org.olf.dcb.core.model.Location::getCode))
			.name(getValueOrNull(item, Item::getLocation, org.olf.dcb.core.model.Location::getName))
			.build();
	}

	private static Status mapStatus(Item item) {
		final var statusCode = getValueOrNull(item, Item::getStatus, ItemStatus::getCode);

		return new Status(getValueOrNull(statusCode, Enum::name));
	}

	private static Agency mapAgency(Item item) {
		final var agency = getValueOrNull(item, Item::getAgency);

		if (agency == null) {
			return null;
		}

		return Agency.builder()
			.code(getValueOrNull(agency, DataAgency::getCode))
			.name(getValueOrNull(agency, DataAgency::getName))
			// Name still mapped to description for backward compatibility
			.description(getValueOrNull(agency, DataAgency::getName))
			.build();
	}

	private static Error mapError(AvailabilityReport.Error error) {
		return Error.builder()
			.message(getValueOrNull(error, AvailabilityReport.Error::getMessage))
			.build();
	}

	@Value
	@Builder
	@Serdeable
	public static class ARVItem {
		String id;
		Status status;
		@Nullable
		Instant dueDate;
		Location location;
		String barcode;
		String callNumber;
		// The host LMS of the agency associated with the item
		@Nullable
		String hostLmsCode;
		// The host LMS the item came from
		@Nullable
		String sourceHostLmsCode;

		// owningContext was added to allow an item to be owned by a library - this is for the shared system setup
    // E.G. the System COOLCAT is shared by several libraries. It is important in the context of resolution to
    // now which context is in force - because for one library and item type may map to CIRC but for another
    // it may map to NONCIRC. This is a first step to surfacing this mapped value in an RTAC response
		String owningContext;

		Boolean isRequestable;
		@Nullable
		Boolean isSuppressed;
		Integer holdCount;
		Instant availabilityDate;
		String localItemType;
		String canonicalItemType;
		String localItemTypeCode;
		@Schema(description = "The items specific institution")
		Agency agency;
		@Nullable
    String rawVolumeStatement;
		@Nullable
		String parsedVolumeStatement;

		Map<String,String> rawDataValues;
		List<String> decisionLogEntries;
	}

	@Value
	@Builder
	@Serdeable
	public static class Status {
		String code;
	}

	@Value
	@Builder
	@Serdeable
	public static class Location {
		String code;
		String name;
	}

	@Value
	@Builder
	@Serdeable
	public static class Error {
		String message;
	}

	@Data
	@Value
	@Builder
	@Serdeable
	public static class Agency {
		String code;
		String name;
		String description;
	}
}
