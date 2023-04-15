package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value = "agency")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class ShelvingLocation {

        @NonNull
        @Id
        @TypeDef( type = DataType.UUID)
        private UUID id;

        @NonNull
        @Size(max = 32)
        private String code;

        @NonNull
        @Size(max = 200)
        private String name;

	/**
	 * The host system on which this shelving location is used (Availability statements from this host LMS will use
	 * the code of this entity to denote the location)
	 */
        @NonNull
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        private DataHostLms hostSystem;

	/**
	 * If we know it, the agency that this shelving location is attached to, so we can work out the owning
	 * institution.
	 */
	@Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        private DataAgency agency;

	/**
	 * Specify a loan policy for this shelvingLocation - null means no specific policy
	 * "LOANABLE" - items from this shelving location can be loaned as a general rule - a positive assertion
	 * "REFERENCE" - a reference only collection which cannot be loaned - a negative assertion
	 * "
	 */
	@Nullable
	private String loanPolicy;
}
