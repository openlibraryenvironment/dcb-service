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
   @Nullable String LCCN,
   @Nullable String ISBN,
   @Nullable String itemType,
   @Nullable String author,
   @Nullable String title,
   @Nullable String edition,
   @Nullable String publicationInformation,
   @Nullable String physicalDescription,
   @Nullable String seriesStatement,
   @Nullable String annotationOrSummaryNote,
   @Nullable String topicalSubjectHeading,
   @Nullable String personalNameAddedEntry){}