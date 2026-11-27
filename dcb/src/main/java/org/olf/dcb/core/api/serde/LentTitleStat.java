package org.olf.dcb.core.api.serde;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/** A work other libraries asked this library to supply, and how often. */
@Serdeable
@Introspected
public record LentTitleStat(UUID clusterId, String title, Long requestCount) {
}
