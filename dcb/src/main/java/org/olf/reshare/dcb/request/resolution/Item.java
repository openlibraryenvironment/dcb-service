package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public final class Item {
	private final String id;
	private final String hostLmsCode;
}
