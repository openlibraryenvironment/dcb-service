package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record PatronGroupDemandStat(
	String patronGroup,
	Long requestCount
) {}
