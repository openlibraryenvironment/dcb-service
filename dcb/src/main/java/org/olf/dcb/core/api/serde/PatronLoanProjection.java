package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Exactly the columns the patron-loans query selects — nothing derived, for the same
 * reason {@link PatronRequestSummaryProjection} carries none.
 *
 * <p>{@code localItemId} and {@code patronHostlmsCode} are here because the loan is not
 * finished in the database: DCB records that a request reached LOANED and how many times
 * it has been renewed, but no due date, so the two of them are what the controller needs
 * in order to go and ask the borrowing library.
 */
@Introspected
public record PatronLoanProjection(
	@NotNull UUID id,
	@Nullable String title,
	@Nullable UUID bibClusterId,
	@Nullable String pickupLocationCode,
	@Nullable String pickupLocationName,
	/** The VIRTUAL item at the patron's own library — the copy they are holding. */
	@Nullable String localItemId,
	@Nullable String localRequestId,
	@Nullable String localHoldingId,
	@Nullable String localBibId,
	/** The borrowing library, which is the system that knows when it is due back. */
	@Nullable String patronHostlmsCode,
	/** What the last tracking sweep saw. A live read may be ahead of it. */
	@Nullable Integer localRenewalCount,
	@Nullable String renewalStatus,
	@Nullable Instant dateUpdated
) {}
