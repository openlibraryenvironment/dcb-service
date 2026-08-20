package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

// Distribution of how many distinct source systems hold each work. Doubles as the confidence
// signal for every other collection metric: a corpus where nearly every cluster has exactly one
// holder means the matching is under-clustering, which inflates the unique-title counts.
@Serdeable
@Introspected
public record ClusterSizeStat(
	Integer holderCount,
	Long clusterCount) {
}
