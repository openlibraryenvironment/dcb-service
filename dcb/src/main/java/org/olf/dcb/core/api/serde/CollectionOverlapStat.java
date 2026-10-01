package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

// A selected library (left) and one other holder of its works (right), with the number of works
// both hold. One row per selected library and peer; never the full matrix.
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
