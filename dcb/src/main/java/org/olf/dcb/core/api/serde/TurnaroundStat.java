package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Turnaround time distribution (in seconds) to reach a target status. Percentiles, not a mean:
 * turnaround is a skewed distribution and a single stuck request drags the average into fiction.
 * p50 is the typical experience; p95 is the tail an admin needs to see.
 */
@Serdeable
@Introspected
public record TurnaroundStat(
	Double p50Seconds,
	Double p95Seconds
) {}
