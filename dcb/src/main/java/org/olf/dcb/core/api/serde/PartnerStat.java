package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One trading partner and how much traffic passed between them and the caller's library.
 * Direction comes from which list it appears in - see DashboardMetrics.
 *
 * partnerName is NULL when no library row maps to that Host LMS - a system with requests that is
 * not onboarded as a library - so the caller must fall back to the code. One Host LMS can serve
 * several libraries, so the name lists all of them rather than picking one.
 */
@Serdeable
@Introspected
public record PartnerStat(
	String partnerCode,
	@Nullable String partnerName,
	Long requestCount) {}
