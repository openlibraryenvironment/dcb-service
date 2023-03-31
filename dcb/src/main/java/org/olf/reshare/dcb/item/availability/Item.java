package org.olf.reshare.dcb.item.availability;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class Item {
	private final String id;
	private final Status status;
	private final Location location;
	private final String barcode;
	private final String callNumber;
	private final String hostLmsCode;
}
