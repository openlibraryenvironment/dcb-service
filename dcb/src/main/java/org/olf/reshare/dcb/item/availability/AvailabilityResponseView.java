package org.olf.reshare.dcb.item.availability;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

import java.util.List;

@Data
@Serdeable
public class AvailabilityResponseView {
	private final List<Item> itemList;
	private final String bibRecordId;
	private final String agencyCode;
}


