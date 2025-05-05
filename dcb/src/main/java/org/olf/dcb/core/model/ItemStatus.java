package org.olf.dcb.core.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@Serdeable
public class ItemStatus {
	private final ItemStatusCode code;
}
