package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Checkout rate: of the requests placed in scope, how many actually reached the shelf
 * (ever hit LOANED). reachedCount / totalCount is the rate; the raw counts are kept so
 * the UI can show "N of M". Works consortium-wide or per library.
 */
@Serdeable
@Introspected
public record CheckoutRateStat(
	Long reachedCount,
	Long totalCount
) {}
