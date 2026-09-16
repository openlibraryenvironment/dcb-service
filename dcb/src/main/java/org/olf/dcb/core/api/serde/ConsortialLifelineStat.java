package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * Titles this library supplies to the rest of the consortium - its lifeline contribution.
 *
 * <p>Everything projected from a bib record is nullable. Micronaut Data throws
 * DataAccessException on a null read into a non-null constructor argument, so a catalogue
 * record with no ISBN or no author took the whole endpoint to 500 rather than leaving a
 * cell empty. Same rule as PartnerStat.partnerName, found the same way.
 */
@Serdeable
@Introspected
public record ConsortialLifelineStat(
	UUID clusterId,
	@Nullable String title,
	@Nullable String author,
	@Nullable String isbn,
	@Nullable String localBibId,
	Long supplyCount
) {}
