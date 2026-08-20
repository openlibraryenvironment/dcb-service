package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Per-supplier responsiveness SLA: median time (seconds) from a request being placed
 * at the supplying agency to that supplier confirming the item. The lender-side
 * equivalent of borrower turnaround - who answers fast, who drags.
 */
@Serdeable
@Introspected
public record SupplierResponseStat(
	String supplierCode,
	Double medianResponseSeconds,
	Long sampleCount
) {}
