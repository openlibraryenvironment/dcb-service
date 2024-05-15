package org.olf.dcb.core.interaction.polaris;

import java.time.Instant;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
@Serdeable
public class BibsPagedGetParams {
	private final Instant startdatemodified;
	private final Integer lastId;
	private final Integer nrecs;
}
