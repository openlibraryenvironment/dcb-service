package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 *
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class Grant {

        @NotNull
        @NonNull
        @Id
        @TypeDef( type = DataType.UUID)
        private UUID id;

        @ToString.Include
        @NonNull
        String grantResourceOwner;

        @ToString.Include
        @NonNull
        String grantResourceType;

        @ToString.Include
        @NonNull
        String grantResourceId;

        @ToString.Include
        @NonNull
        String grantedPerm;

        @ToString.Include
        @NonNull
        String granteeType;

        @ToString.Include
        @NonNull
        String grantee;

        @ToString.Include
        @NonNull
        Boolean grantOption;
}
