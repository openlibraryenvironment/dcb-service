package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Reciprocity: for a library, how much it borrowed from the network versus how much it supplied
 * to it. Net flow (suppliedCount - borrowedCount) reveals net givers vs net takers - a consortium
 * equity signal. Left to the caller so both raw counts remain available to the UI.
 */
@Serdeable
@Introspected
public record NetFlowStat(
	String libraryCode,
	Long borrowedCount,
	Long suppliedCount
) {}
