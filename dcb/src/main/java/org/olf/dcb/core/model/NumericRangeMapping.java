package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.TypeDef;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 *  ID - Hash of context+domain+lowerBound
 */
@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class NumericRangeMapping {

        @NotNull
        @NonNull
        @Id
        @TypeDef(type = DataType.UUID)
        private UUID id;


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
        private String targetContext;

        @NotNull
        @NonNull
        private String mappedValue;

				@Nullable
				private Instant lastImported;

				@Nullable
				@Builder.Default
				private Boolean deleted = false;

}
