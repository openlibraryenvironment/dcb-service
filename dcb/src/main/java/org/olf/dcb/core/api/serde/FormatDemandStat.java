package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Requesting demand broken down by canonical format (derivedType from the clustered
 * bib metadata, e.g. Books / Serials / Visual materials). Consortial collection analysis.
 */
@Serdeable
@Introspected
public record FormatDemandStat(
	String format,
	Long requestCount
) {}
