package org.olf.reshare.dcb.api;

import java.util.List;

import org.olf.reshare.dcb.item.availability.Status;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class AvailabilityResponse {
	@Nullable
	private final List<Item> itemList;
	@Nullable
	private final String bibRecordId;
	@Nullable
	private final String hostLmsCode;

	@Data
	@Serdeable
	public static class Item {
		@Nullable
		private final String id;
		@Nullable
		private final Status status;
	}
}


