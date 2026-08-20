package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

// Per-Host-LMS collection shape, derived purely from ingested bibs and their clustering.
// Needs no patron requests, so it reports on the collection as catalogued rather than as used.
// clusterCount = distinct works this source contributes to; uniqueTitleCount = the subset of
// those works no other source system holds a bib for.
@Serdeable
@Introspected
public record CollectionProfileStat(
	UUID sourceSystemId,
	/** Host LMS code - the stable identifier for a library, not its display name. */
	String sourceSystemCode,
	Long clusterCount,
	Long uniqueTitleCount) {
}
