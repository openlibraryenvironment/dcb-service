package org.olf.reshare.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Serdeable
@Data
public final class ClusteredBib {
	private final UUID id;
	private final List<Bib> bibs;
}
