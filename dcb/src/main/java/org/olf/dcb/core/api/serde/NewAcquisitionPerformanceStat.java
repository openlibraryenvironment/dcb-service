package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

/**
 * Recently acquired titles, and how much consortial supply they have carried since.
 *
 * <p>Everything projected from a bib record is nullable. Micronaut Data throws
 * DataAccessException on a null read into a non-null constructor argument, so a catalogue
 * record with no ISBN or no author took the whole endpoint to 500 rather than leaving a
 * cell empty. Same rule as PartnerStat.partnerName, found the same way.
 */
@Serdeable
@Introspected
public record NewAcquisitionPerformanceStat(
	UUID clusterId,
	@Nullable String title,
	@Nullable String author,
	@Nullable String localBibId,
	Instant dateAdded,
	Long supplyCount
) {}
