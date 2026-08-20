package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

// One unordered pair of source systems and the number of works both hold. Emitted once per
// pair (left < right), so the consumer mirrors it to draw a full matrix.
//
// Identified by host LMS code, not name. Code is the stable identifier a consortium uses for a
// library; name is display text that is free to change, may be duplicated between libraries,
// and is not what anything downstream keys on.
@Serdeable
@Introspected
public record CollectionOverlapStat(
	UUID leftSystemId,
	String leftSystemCode,
	UUID rightSystemId,
	String rightSystemCode,
	Long sharedTitleCount) {
}
