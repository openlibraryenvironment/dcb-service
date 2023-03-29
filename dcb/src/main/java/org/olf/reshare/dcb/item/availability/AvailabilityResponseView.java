package org.olf.reshare.dcb.item.availability;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class AvailabilityResponseView {
	private final List<Item> itemList;
	private final String bibRecordId;
	private final String hostLmsCode;
}
