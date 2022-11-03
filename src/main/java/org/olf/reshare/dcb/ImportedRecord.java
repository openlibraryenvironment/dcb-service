package org.olf.reshare.dcb;

import javax.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.NonNull;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record ImportedRecord ( 
	
	@NonNull @NotEmpty String title
	) {}
