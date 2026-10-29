package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.serde.annotation.Serdeable;

/** Ids and dates only: the apps load the requests through their own scoped query. */
@Serdeable
public record WatchedRequestPage(@JsonInclude(JsonInclude.Include.ALWAYS) List<Watched> content, long totalSize) {
	// Serde leaves out an empty list unless told otherwise, and a client reading content then fails
	public WatchedRequestPage {
		content = content == null ? List.of() : List.copyOf(content);
	}

	@Serdeable
	public record Watched(UUID patronRequestId, Instant dateCreated, Instant dateLastViewed) {
	}
}
