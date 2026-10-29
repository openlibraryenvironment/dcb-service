package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;

/** Ids and dates only: the apps load the requests through their own scoped query. */
@Serdeable
public record WatchedRequestPage(List<Watched> content, long totalSize) {
	@Serdeable
	public record Watched(UUID patronRequestId, Instant dateCreated, Instant dateLastViewed) {
	}
}
