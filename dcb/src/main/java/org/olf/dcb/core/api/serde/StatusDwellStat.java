package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Median dwell time (seconds) a request spends in a given status before moving on -
 * the time-in-status / bottleneck view. Ordered so the biggest stalls surface first.
 * sampleCount is the number of dwell observations behind the median.
 */
@Serdeable
@Introspected
public record StatusDwellStat(
	String status,
	Double medianDwellSeconds,
	Long sampleCount
) {}
