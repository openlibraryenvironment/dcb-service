package org.olf.dcb.api;

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
	@Nullable
	private final List<Error> errors;

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
		@Nullable
		private final Boolean isRequestable;
		@Nullable
		private final Integer holdCount;
		@Nullable
		private final String availabilityDate;
		@Nullable
		private final String localItemType;
		@Nullable
		private final String canonicalItemType;
		@Nullable
		private final Agency agency;
		// The host LMS of the agency associated with the item
		@Nullable
		private final String hostLmsCode;
		// The host LMS the item came from
		@Nullable
		private final String sourceHostLmsCode;
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

	@Data
	@Serdeable
	public static class Error {
		@Nullable
		private final String message;
	}

	@Data
	@Serdeable
	public static class Agency {
		@Nullable
		private final String code;
		@Nullable
		private final String description;
	}
}


