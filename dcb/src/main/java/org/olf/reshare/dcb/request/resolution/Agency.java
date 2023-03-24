package org.olf.reshare.dcb.request.resolution;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public final class Agency {
	private final String code;
}
