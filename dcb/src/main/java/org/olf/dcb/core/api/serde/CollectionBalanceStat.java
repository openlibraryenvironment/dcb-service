package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record CollectionBalanceStat(
	Long borrowedCount,
	Long suppliedCount
) {
	// Calculated property: Positive = Net Lender, Negative = Net Borrower
	public Long getNetBalance() {
		return suppliedCount - borrowedCount;
	}
}
