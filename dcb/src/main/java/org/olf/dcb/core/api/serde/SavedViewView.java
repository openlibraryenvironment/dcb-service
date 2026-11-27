package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;

/** One saved grid view belonging to the caller. */
@Serdeable
public record SavedViewView(
	UUID id,
	String name,
	String viewType,
	Map<String, Object> queryState,
	Instant dateCreated,
	Instant dateUpdated) {
}
