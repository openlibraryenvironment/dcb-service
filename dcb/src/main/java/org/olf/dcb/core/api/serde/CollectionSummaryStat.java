package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Headline collection-analysis figures: how many DISTINCT titles (clusters) the network
 * requested versus the total request volume. A high total-to-unique ratio means the same
 * titles are being requested repeatedly - candidates for local acquisition.
 */
@Serdeable
@Introspected
public record CollectionSummaryStat(
	Long uniqueTitlesRequested,
	Long totalRequests
) {}
