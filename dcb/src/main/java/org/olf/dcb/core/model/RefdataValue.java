package org.olf.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * The ID UUID should be created as the UUID5 Hash of the context and the category to give stable identifiers
 */
@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class RefdataValue {

    @NotNull
    @NonNull
    @Id
    @TypeDef( type = DataType.UUID)
    private UUID id;

    @NotNull
    @NonNull
    @Size(max=64)
    private String category;

    @NotNull
    @NonNull
    @Size(max=64)
    private String context;

    @NotNull
    @NonNull
    @Size(max=255)
    private String value;

    @NotNull
    @NonNull
    @Size(max=255)
    private String label;
}

