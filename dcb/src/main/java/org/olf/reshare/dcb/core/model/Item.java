package org.olf.reshare.dcb.core.model;

import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.time.ZonedDateTime;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
public class Item {
	private final String id;
	private final ItemStatus status;
	@Nullable
	private final ZonedDateTime dueDate;
	private final Location location;
	private final String barcode;
	private final String callNumber;
	private final String hostLmsCode;
	private final Boolean isRequestable;
	private final Integer holdCount;

	public boolean isAvailable() {
		return getStatus().getCode() == AVAILABLE;
	}
}
