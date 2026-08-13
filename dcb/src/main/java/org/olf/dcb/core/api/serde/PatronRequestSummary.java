package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.api.discovery.PatronRequestDiscoveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * This is a class to return a very specific set of fields for discovery services.
 * As we don't really want to expose everything to patrons - for now.
 *
 * The API shape, not the query shape: {@link PatronRequestSummaryProjection} is what
 * the repository selects, and {@link org.olf.dcb.core.api.discovery.PatronStatusMapper#enrich}
 * derives {@code discoveryStatus} / {@code statusDescription} on the way out here.
 * **/

@Serdeable
@Introspected
public record PatronRequestSummary(
	@NotNull UUID id,
	// The raw DCB state machine value. Discovery services that want to do their own
	// mapping read this; the two fields below are for those that don't.
	@Nullable String status,
	@Nullable PatronRequestDiscoveryStatus discoveryStatus,
	@Nullable String statusDescription,
	// From main: the terminal outcome of the request, independent of the patron-facing
	// status mapping above. Kept alongside rather than folded in -- a discovery service
	// that renders discoveryStatus still wants to know how a finished request ended.
	@Nullable String outcome,
	@Nullable String nextExpectedStatus,
	@Nullable Long timeInState,
	@Nullable String errorMessage,
	@Nullable String title,
	@Nullable String pickupLocationCode,
	// Falls back to the code when it resolves to no known location
	@Nullable String pickupLocationName,
	// RET-LOCAL means a same-library hold; discovery services use this to
	// split "my requests" from "my local requests"
	@Nullable String activeWorkflow,
	// Lets a discovery UI link the request back to its catalogue record
	@Nullable UUID bibClusterId,
	@Nullable Instant dateCreated,
	@Nullable Instant dateUpdated
) {}
