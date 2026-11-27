package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/** How often other libraries asked this library to supply a canonical format. */
@Serdeable
@Introspected
public record LendingFormatStat(String format, Long requestCount) {
}
