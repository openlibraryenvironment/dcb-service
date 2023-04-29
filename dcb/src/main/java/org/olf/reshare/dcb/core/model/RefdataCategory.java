package org.olf.reshare.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * A RefdataCategory is a domain object that owns a set of values. Examples of RefdataCategories are PatronTypes,
 * Item Status Codes, ServiceTypes, etc. DCB introduces a new concept into this established pattern: Context.
 * Our modelling aim here is to allow us to represent a category in different contexts - for example
 * PatronTypes in the context of the KC-Towers cluster vs PatronTypes in the context of the canonical DCB Hub.
 * The term context was chosen because sometimes we may want to have broad context - e.g. "MOBIUS" if refdata
 * is pre-coordinated over a large number of installations. At the same time  it is expected that there will be an
 * explicit context for each HostLMS.
 *
 * The ID UUID should be created as the UUID5 Hash of the context and the category to give stable identifiers
 */
@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class RefdataCategory {

    @NotNull
    @NonNull
    @Id
    @TypeDef( type = DataType.UUID)
    private UUID id;

    @Nullable
    @DateCreated
    private Instant dateCreated;

    @Nullable
    @DateUpdated
    private Instant dateUpdated;

    @NotNull
    @NonNull
    @Size(max=255)
    private String context;

    @NotNull
    @NonNull
    @Size(max=255)
    private String category;
}

