package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.UUID;

@Serdeable
@Introspected
public record NewAcquisitionPerformanceStat(
	UUID clusterId,
	String title,
	String author,
	String localBibId,
	Instant dateAdded,
	Long supplyCount
) {}
