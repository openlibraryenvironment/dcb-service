package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Exactly the columns the patron request summary queries select — nothing derived.
 *
 * This is deliberately separate from {@link PatronRequestSummary}: Micronaut Data
 * builds a record projection through its canonical constructor, so every component
 * must map to a selected column. A derived component (even a @Transient one) has no
 * getter in the resulting introspection and the mapper fails at render time with
 * "Constructor argument [x] must have an associated getter".
 *
 * The repository returns this; {@link org.olf.dcb.core.api.discovery.PatronStatusMapper#enrich}
 * turns it into the API record.
 */
@Introspected
public record PatronRequestSummaryProjection(
	@NotNull UUID id,
	@Nullable String status,
	// From main's outcome_code. Selected, not derived, so it belongs here.
	@Nullable String outcome,
	@Nullable String nextExpectedStatus,
	@Nullable Long timeInState,
	@Nullable String errorMessage,
	@Nullable String title,
	@Nullable String pickupLocationCode,
	@Nullable String pickupLocationName,
	@Nullable String activeWorkflow,
	@Nullable UUID bibClusterId,
	@Nullable Instant dateCreated,
	@Nullable Instant dateUpdated
) {}
