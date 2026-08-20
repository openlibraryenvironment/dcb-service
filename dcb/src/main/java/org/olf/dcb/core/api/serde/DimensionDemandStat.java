package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Generic "requesting demand by a collection dimension" cell - the dimension (format,
 * language, subject, publication decade) is chosen by the caller; category is the value
 * within that dimension. Consortial collection analysis.
 */
@Serdeable
@Introspected
public record DimensionDemandStat(
	String category,
	Long requestCount
) {}
