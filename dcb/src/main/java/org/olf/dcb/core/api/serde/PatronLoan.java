package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/**
 * One thing a patron currently has out through DCB.
 *
 * <p>Carries no local identifier a discovery service could act on. The request id is the
 * only handle, because every action a patron may take is addressed by request.
 */
@Serdeable
@Introspected
public record PatronLoan(
	@NotNull UUID id,
	@Nullable String title,
	@Nullable UUID bibClusterId,
	@Nullable String pickupLocationCode,
	@Nullable String pickupLocationName,
	/**
	 * When the borrowing library says it is due back. NULL MEANS UNKNOWN and never "no
	 * due date": some Host LMS report item status from a payload carrying no date, and
	 * the library was unreachable when this was read. {@link #dueDateKnown} is what
	 * separates the two for a client that would otherwise render an empty cell.
	 */
	@Nullable Instant dueDate,
	/**
	 * False when the borrowing library could not be asked, or answered without a date.
	 * A client must say so rather than leave the column blank.
	 */
	boolean dueDateKnown,
	@Nullable Integer renewalCount,
	/**
	 * Whether the ITEM is renewable, as the borrowing library reports it. Absent where
	 * that system does not say. Not on its own permission to renew: DCB's own
	 * {@code renewalStatus} can forbid it after the item has been recalled.
	 */
	@Nullable Boolean renewable,
	@Nullable String renewalStatus,
	@Nullable Instant dateUpdated
) {}
