package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record PickupLocationDemandStat(
	String pickupLocationCode,
	String pickupLocationName,
	Long requestCount
) {}
