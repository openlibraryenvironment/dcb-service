package org.olf.dcb.core.api.serde;

import io.micronaut.serde.annotation.Serdeable;

/** Always 200 while the route exists, so a 404 can only mean a dcb-service without it. */
@Serdeable
public record PreferenceAvailability(boolean available, int maxSavedViews, int maxWatched) {
}
