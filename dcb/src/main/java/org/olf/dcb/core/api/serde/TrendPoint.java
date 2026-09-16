package org.olf.dcb.core.api.serde;

import java.time.Instant;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One bucket of a percentile trend: how long the metric took for the requests that completed
 * inside that bucket. Percentiles, not a mean, for the reason {@link TurnaroundStat} gives.
 *
 * <p>Empty buckets are ABSENT rather than zero, unlike the flow time series where a zero count
 * is a fact. A bucket with no observations has no median, and a zero would read as instant.
 * {@code sampleCount} is how far the bucket can be trusted.
 */
@Serdeable
@Introspected
public record TrendPoint(
	Instant bucket,
	Double p50Seconds,
	Double p95Seconds,
	Long sampleCount
) {}
