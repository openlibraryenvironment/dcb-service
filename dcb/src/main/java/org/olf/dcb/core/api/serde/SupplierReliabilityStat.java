package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Per-supplying-library reliability: how many requests it actually fulfilled versus failed.
 * Distinguishes "supplies a lot" from "actually delivers" - volume alone hides a bad partner.
 * The success rate is left to the caller (fulfilledCount / (fulfilledCount + failedCount)).
 */
@Serdeable
@Introspected
public record SupplierReliabilityStat(
	String supplierCode,
	Long fulfilledCount,
	Long failedCount
) {}
