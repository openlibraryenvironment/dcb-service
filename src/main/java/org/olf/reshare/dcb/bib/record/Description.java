package org.olf.reshare.dcb.bib.record;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.soabase.recordbuilder.core.RecordBuilder;


@Serdeable
@RecordBuilder
public record Description (
   @NonNull @NotEmpty UUID id,
   @Nullable String description,
   @Nullable List<String> descriptions
   ){}