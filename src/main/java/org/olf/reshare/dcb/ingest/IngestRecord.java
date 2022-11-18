package org.olf.reshare.dcb.ingest;

import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.NonNull;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record IngestRecord(
		@NonNull @NotEmpty UUID uuid,
		@NonNull @NotEmpty String title
) {
	
}
