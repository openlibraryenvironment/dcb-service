package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One partner and the traffic in BOTH directions, ranked on the total.
 *
 * This is the question the two directional lists in DashboardMetrics cannot answer between
 * them: a partner sitting sixth in each can out-total one ranked third in one, and would appear
 * in neither top ten. The breakdown is kept alongside the total so the direction is not lost -
 * a partner we borrow from constantly and never supply is a different relationship from an even
 * one, and the totals alone cannot tell them apart.
 *
 * partnerName is NULL for a Host LMS with traffic that is not onboarded as a library, so the
 * caller must fall back to the code.
 */
@Serdeable
@Introspected
public record TradingPartnerStat(
	String partnerCode,
	@Nullable String partnerName,
	Long borrowedFromCount,
	Long suppliedToCount,
	Long totalCount) {}
