package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One library's own figures, split by whether the reader collected in person (no van moved) or
 * the item was shipped. libraryName is null for a Host LMS not onboarded as a library, so the
 * caller shows the code; one Host LMS can serve several libraries, so the name lists them all.
 */
@Serdeable
@Introspected
public record LibraryBreakdownStat(
	String libraryCode,
	@Nullable String libraryName,
	Long totalRequests,
	/** Collected in person at the supplying library. No van moved. */
	Long walkUpRequests,
	/** Sent to the reader's own library for collection. */
	Long shippedRequests
) {}
