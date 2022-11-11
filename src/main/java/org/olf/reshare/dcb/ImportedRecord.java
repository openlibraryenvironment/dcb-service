
package org.olf.reshare.dcb;

import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.soabase.recordbuilder.core.RecordBuilder;

@Serdeable
@RecordBuilder
public record ImportedRecord (
   @NonNull @NotEmpty UUID identifier,
   @Nullable String controlNumber,
   @Nullable String controlNumberIdentifier,
   @Nullable String title, 
   @Nullable String itemType){}
	
   