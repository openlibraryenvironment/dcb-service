package org.olf.dcb.item.availability;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
public class AvailabilityResponseView {
	private final List<ARVItem> itemList;
	private final List<Error> errors;
	private final Map<String, Long> timings;
	private final UUID clusteredBibId;

	public static AvailabilityResponseView from(AvailabilityReport report,
		UUID clusteredBibId) {

		final var mappedItems = report.getItems()
			.stream()
			.map(AvailabilityResponseView::mapItem)
			.toList();

		final var mappedErrors = report.getErrors().stream()
			.map(error -> Error.builder()
				.message(error.getMessage())
				.build())
			.toList();
		
		final var timingsMap = new LinkedHashMap<String, Long> ();
			report.getTimings().forEach( tuple -> timingsMap.put(tuple.getT1(), tuple.getT2()) );
				
		return new AvailabilityResponseView(mappedItems, mappedErrors, timingsMap, clusteredBibId);
	}

	private static ARVItem mapItem(Item item) {
		final var agency = getValueOrNull(item, Item::getAgency);
		final var agencyCode = getValueOrNull(agency, DataAgency::getCode);
		final var agencyName = getValueOrNull(agency, DataAgency::getName);
		final var owningContext = getValueOrNull(item, Item::getOwningContext);

		final var mappedAgency = agency != null
			? new Agency(agencyCode, agencyName)
			: null;

		return new ARVItem(item.getLocalId(),
			new Status(item.getStatus().getCode().name()), item.getDueDate(),
			new Location(item.getLocation().getCode(), item.getLocation().getName()),
			item.getBarcode(), item.getCallNumber(), item.getHostLmsCode(),
			owningContext, item.getIsRequestable(), item.getSuppressed(),
			item.getHoldCount(), item.getAvailableDate(), item.getLocalItemType(),
			item.getCanonicalItemType(), item.getLocalItemTypeCode(), mappedAgency,
			item.getRawVolumeStatement(), item.getParsedVolumeStatement(),
			item.getRawDataValues(),
			item.getDecisionLogEntries()
		);
	}

	@Data
	@Serdeable
	public static class ARVItem {
		private final String id;
		private final Status status;
		@Nullable
		private final Instant dueDate;
		private final Location location;
		private final String barcode;
		private final String callNumber;
		private final String hostLmsCode;

		// owningContext was added to allow an item to be owned by a library - this is for the shared system setup
    // E.G. the System COOLCAT is shared by several libraries. It is important in the context of resolution to
    // now which context is in force - because for one library and item type may map to CIRC but for another
    // it may map to NONCIRC. This is a first step to surfacing this mapped value in an RTAC response
		private final String owningContext;

		private final Boolean isRequestable;
		@Nullable
		private final Boolean isSuppressed;
		private final Integer holdCount;
		private final Instant availabilityDate;
		private final String localItemType;
		private final String canonicalItemType;
		private final String localItemTypeCode;
		@Schema(description = "The items specific institution")
		private final Agency agency;
		@Nullable
    private final String rawVolumeStatement;
		@Nullable
    private final String parsedVolumeStatement;

		private final Map<String,String> rawDataValues;
		private final List<String> decisionLogEntries;
	}

	@Data
	@Serdeable
	public static class Status {
		private final String code;
	}

	@Data
	@Serdeable
	public static class Location {
		private final String code;
		private final String name;
	}

	@Data
	@Builder
	@Serdeable
	public static class Error {
		private final String message;
	}

	@Data
	@Serdeable
	public static class Agency {
		private final String code;
		private final String description;
	}
}
