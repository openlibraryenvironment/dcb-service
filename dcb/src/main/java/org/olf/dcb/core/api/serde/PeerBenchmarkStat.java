package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Per-library figures for peer benchmarking. Rates are left to the caller (checkoutCount /
 * totalRequests, successCount / (successCount + failedCount)).
 *
 * libraryName is NULL when no library row maps to that Host LMS - a system with requests that
 * is not onboarded as a library - so the caller must fall back to the code. One Host LMS can
 * serve several libraries, so the name lists all of them rather than picking one.
 */
@Serdeable
@Introspected
public record PeerBenchmarkStat(
	String libraryCode,
	@Nullable String libraryName,
	Long totalRequests,
	Long checkoutCount,
	Long successCount,
	Long failedCount
) {}
