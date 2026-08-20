package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

// Format mix per source system, from bib_record.derived_type. Counts WORKS, not records, so it
// reconciles against the collection profile.
//
// derivedType is @Nullable because derived_type is varchar(32) with no NOT NULL
// (V1__Initial_schema.sql): an ingest that could not derive one produces a null that reaches
// this constructor. Untyped works are reported rather than dropped, so the totals still add up.
@Serdeable
@Introspected
public record SourceFormatStat(
	UUID sourceSystemId,
	/** Host LMS code - the stable identifier for a library, not its display name. */
	String sourceSystemCode,
	@Nullable String derivedType,
	Long titleCount) {
}
