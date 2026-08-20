package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * A single point in a flow time-series: how many requests transitioned INTO {@code series}
 * (a DCB status) during the bucket starting at {@code bucket}. Derived from patron_request_audit,
 * so it is a flow (events-per-period) metric, not a stock (concurrent count) metric.
 */
@Serdeable
@Introspected
public record TimeSeriesPoint(
	Instant bucket,
	String series,
	Long count
) {}
