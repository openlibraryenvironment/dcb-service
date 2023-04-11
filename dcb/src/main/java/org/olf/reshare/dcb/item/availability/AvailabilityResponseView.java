package org.olf.reshare.dcb.item.availability;

import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class AvailabilityResponseView {
	private final List<Item> itemList;
	private final String bibRecordId;
	private final String hostLmsCode;

	public static AvailabilityResponseView from(List<org.olf.reshare.dcb.core.model.Item> items,
		String bibRecordId, String hostLmsCode) {

		final var mappedItems = items.stream()
			.map(item -> new Item(item.getId(),
				new Status(item.getStatus().getCode().name()), item.getDueDate(),
				new Location(item.getLocation().getCode(), item.getLocation().getName()),
				item.getBarcode(), item.getCallNumber(), item.getHostLmsCode()))
			.collect(toList());

		return new AvailabilityResponseView(mappedItems, bibRecordId, hostLmsCode);
	}

	@Data
	@Serdeable
	public static class Item {
		private final String id;
		private final Status status;
		@Nullable
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
		private final ZonedDateTime dueDate;
		private final Location location;
		private final String barcode;
		private final String callNumber;
		private final String hostLmsCode;
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
}
