package org.olf.reshare.dcb.request.resolution;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public final class Holdings {
	private final Agency agency;
	private final List<Item> items;
}
