package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.olf.dcb.core.audit.Auditable;
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
public class ReferenceValueMapping implements Auditable{

    @NotNull
    @NonNull
    @Id
    @TypeDef( type = DataType.UUID)
    private UUID id;

    @NotNull
    @NonNull
    @Size(max=64)
    private String fromCategory;

    @NotNull
    @NonNull
    @Size(max=64)
    private String fromContext;

    @NotNull
    @NonNull
    @Size(max=255)
    private String fromValue;


    @NotNull
    @NonNull
    @Size(max=64)
    private String toCategory;

    @NotNull
    @NonNull
    @Size(max=64)
    private String toContext;

    @NotNull
    @NonNull
    @Size(max=255)
    private String toValue;

    @Nullable
    private Boolean reciprocal;

		// The below values were added to the DB in V1_0_1_010__extendReferenceValueMapping.sql
		// and deleted was added in V1_0_1_012__softDeleteReferenceValueMapping.sql

		@Nullable
		private String label;

		@Nullable
		private Instant lastImported;

		@Nullable
		@Builder.Default
		private Boolean deleted = false;

		@ToString.Include
		@Nullable
		private String lastEditedBy;

		@ToString.Include
		@Nullable
		private String reason;

}
