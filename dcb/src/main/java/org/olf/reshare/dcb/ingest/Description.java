package org.olf.reshare.dcb.ingest;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;

@Serdeable
public record Description(
	@NonNull @NotEmpty UUID id,
	@Nullable String description,
	@Nullable List<String> descriptions
) {
}
