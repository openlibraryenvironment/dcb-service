package org.olf.reshare.dcb.bib.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.serde.annotation.Serdeable;
import io.soabase.recordbuilder.core.RecordBuilder;

@Serdeable
@RecordBuilder
@ReflectiveAccess
public record BibRecord (

	@ReflectiveAccess
	@NotNull @NonNull UUID id,
	

	@ReflectiveAccess
	@NotNull @NonNull String title
	) {}
