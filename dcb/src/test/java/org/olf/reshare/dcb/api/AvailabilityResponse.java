package org.olf.reshare.dcb.api;

import java.util.List;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class AvailabilityResponse {
	@Nullable
	private final List<Item> itemList;
	@Nullable
	private final UUID clusteredBibId;

	@Data
	@Serdeable
	public static class Item {
		@Nullable
		private final String id;
		@Nullable
		private final Status status;
		@Nullable
		private final String dueDate;
		@Nullable
		private final Location location;
		@Nullable
		private final String barcode;
		@Nullable
		private final String callNumber;
	}

	@Data
	@Serdeable
	public static class Status {
		@Nullable
		private final String code;
	}

	@Data
	@Serdeable
	public static class Location {
		@Nullable
		private final String code;
		@Nullable
		private final String name;
	}
}


