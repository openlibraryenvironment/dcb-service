package org.olf.dcb.core.api.serde;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Everything the insights KPI header needs, in ONE round-trip. Collapsing the ~7
 * separate KPI queries into a single combined endpoint cuts round-trips and gives the
 * cache a single natural unit. Heavier below-the-fold panels stay lazy on their own endpoints.
 */
@Serdeable
public record DashboardSummary(
	FulfillmentStat fulfillmentCurrent,
	FulfillmentStat fulfillmentPrior,
	TurnaroundStat turnaroundToLoaned,
	CheckoutRateStat checkoutRate,
	CollectionBalanceStat lendBorrowTotals,
	Long savedByReResolution,
	CollectionSummaryStat collectionSummary
) {}
