package org.olf.reshare.dcb.ingest;

import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import org.immutables.value.Value;

import io.micronaut.core.annotation.NonNull;


@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = {"*Def"})
public interface IngestRecordDef {
		@NonNull @NotEmpty UUID uuid();
		@NonNull @NotEmpty String title();
	
}
