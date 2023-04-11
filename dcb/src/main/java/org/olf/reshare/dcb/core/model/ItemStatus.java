package org.olf.reshare.dcb.core.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class ItemStatus {
	private final ItemStatusCode code;
}
