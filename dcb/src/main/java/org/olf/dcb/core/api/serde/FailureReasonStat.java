package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Count of failed requests bucketed by a derived failure reason (e.g. NO_ITEMS_SELECTABLE,
 * CANCELLED, or ERROR_AT_&lt;workflow-state&gt;). The single most actionable breakdown for a
 * library trying to improve its fulfilment rate.
 */
@Serdeable
@Introspected
public record FailureReasonStat(
	String reason,
	Long count
) {}
