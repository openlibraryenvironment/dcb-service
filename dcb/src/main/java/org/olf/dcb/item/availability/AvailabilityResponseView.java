package org.olf.dcb.item.availability;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

		return new AvailabilityResponseView(mappedItems, mappedErrors, clusteredBibId);
	}

	private static ARVItem mapItem(Item item) {
		final var agency = item.getAgency() != null
			? new Agency(item.getAgencyCode(), item.getAgencyName())
			: null;

		return new ARVItem(item.getLocalId(),
			new Status(item.getStatus().getCode().name()), item.getDueDate(),
			new Location(item.getLocation().getCode(), item.getLocation().getName()),
			item.getBarcode(), item.getCallNumber(), item.getHostLmsCode(),
			item.getIsRequestable(), item.getHoldCount(), item.getLocalItemType(),
			item.getCanonicalItemType(),
			item.getLocalItemTypeCode(),
			agency,
			item.getRawVolumeStatement(),
			item.getParsedVolumeStatement()
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
		private final Boolean isRequestable;
		private final Integer holdCount;
		private final String localItemType;
		private final String canonicalItemType;
		private final String localItemTypeCode;
		@Schema(description = "The items specific institution")
		private final Agency agency;
		@Nullable
    private final String rawVolumeStatement;
		@Nullable
    private final String parsedVolumeStatement;
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
