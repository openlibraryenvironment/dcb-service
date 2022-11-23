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
public record ImportedRecord (
   @NonNull @NotEmpty UUID id,
   @Nullable List<Identifier> identifiers, // ICCN, ISBN, ISSN, controlNumber, controlNumberIdentifier
   @Nullable Author mainAuthor,
   @Nullable List<Author> otherAuthors,
   @Nullable Title title,
   @Nullable List<Title> titleInformation,
   @Nullable Edition edition,
   @Nullable List<PublicationInformation> publicationInformation,
   @Nullable List<Description> descriptions
   ){}