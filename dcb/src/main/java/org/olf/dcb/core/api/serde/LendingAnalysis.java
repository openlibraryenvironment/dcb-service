package org.olf.dcb.core.api.serde;

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What a library is asked to lend: the groups of at least the minimum, and everything below it
 * counted together as other, so no small group can point to a reader.
 */
@Serdeable
@Introspected
public record LendingAnalysis<T>(List<T> groups, long other, long total) {
}
