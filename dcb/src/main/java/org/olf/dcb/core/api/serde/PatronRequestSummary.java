package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * This is a class to return a very specific set of fields for discovery services.
 * As we don't really want to expose everything to patrons - for now.
 * **/

@Serdeable
@Introspected
public record PatronRequestSummary(
	@NotNull UUID id,
	@Nullable String status,
	@Nullable String nextExpectedStatus,
	@Nullable Long timeInState,
	@Nullable String errorMessage,
	@Nullable String title,
	@Nullable String pickupLocationCode,
	@Nullable Instant dateCreated,
	@Nullable Instant dateUpdated
) {}
