package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

@Serdeable
@Introspected
public record TopClusterStat(
	UUID clusterId,
	String title,
	Long requestCount
) {}
