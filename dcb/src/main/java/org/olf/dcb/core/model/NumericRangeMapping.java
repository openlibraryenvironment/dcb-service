package org.olf.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
public class NumericRangeMapping {

    // E.G. "GDCL"
    @NotNull
    @NonNull
    @Size(max=64)
    private String context;


    // E.G. SierraIType
    @NotNull
    @NonNull
    @Size(max=64)
    private String domain;

    @NotNull
    @NonNull
    private Long lowerBound;

    @NotNull
    @NonNull
    private Long upperBound;

    @NotNull
    @NonNull
    private String mappedValue;
}
