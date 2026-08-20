package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One cell of the demand heatmap: how many requests were created in a given
 * day-of-week / hour-of-day slot. Powers the staffing-pattern view.
 * dayOfWeek follows Postgres EXTRACT(DOW): 0 = Sunday .. 6 = Saturday.
 */
@Serdeable
@Introspected
public record DemandHeatCell(
	Integer dayOfWeek,
	Integer hourOfDay,
	Long requestCount
) {}
