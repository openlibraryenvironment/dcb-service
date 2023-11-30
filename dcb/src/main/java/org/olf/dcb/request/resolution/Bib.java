package org.olf.dcb.request.resolution;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Data
@Builder
public class Bib {
	private final UUID id;
	private final String sourceRecordId;
	private final UUID sourceSystemId;
}
