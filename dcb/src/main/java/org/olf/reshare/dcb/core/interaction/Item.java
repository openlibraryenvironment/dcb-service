package org.olf.reshare.dcb.core.interaction;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class Item {
	private final String id;
	private final Status status;
}
