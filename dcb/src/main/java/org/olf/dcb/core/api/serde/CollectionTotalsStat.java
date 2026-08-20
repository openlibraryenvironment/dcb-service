package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The consortium's collection in four numbers. What each one does and does not mean, and why
 * {@code distinctTitles} is neither the sum of {@link CollectionProfileStat#clusterCount()} nor
 * {@code count(*) FROM cluster_record}: docs/insights.md part 5.
 *
 * <p>Only as good as the clustering that produced it - read beside {@link ClusterSizeStat}.
 */
@Serdeable
@Introspected
public record CollectionTotalsStat(
	/** Distinct works across the consortium - the deduplicated title count. */
	Long distinctTitles,
	/** Works exactly one source system contributes to. */
	Long singlyHeldTitles,
	/** Distinct (work, source system) pairs - what the consortium holds, duplicates included. */
	Long holdings,
	/** Source systems contributing at least one clustered, non-deleted bib. */
	Long contributingSources
) {}
