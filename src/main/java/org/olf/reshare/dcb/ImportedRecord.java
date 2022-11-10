
package org.olf.reshare.dcb;

import java.util.UUID;

import javax.validation.constraints.NotEmpty;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.soabase.recordbuilder.core.RecordBuilder;

@Serdeable
@RecordBuilder
public record ImportedRecord (
   @NonNull @NotEmpty UUID identifier,
   @Nullable ControlField controlNumber,
   @Nullable ControlField controlNumberIdentifier,
   @Nullable DataField title, 
   @Nullable DataField itemType){}
	
   