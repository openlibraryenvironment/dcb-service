package org.olf.reshare.dcb.core.model;

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

import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A shelving location referenced in item availability reports.
 * Institutions (Universities, Public Libraries, Specialst libraries) are referred to as "Agencies" in our model.
 * Agencies can group together to share HostLMS instances.
 * Library management systems use the concept of Shelving Location to indicate the place an item resides. 
 * Because of the complexity introduced by libraries sharing instances of a system we have the following:
 *   An instance of a HostLMS manages many ShelvingLocations
 *   An instance of a HostLMS is shared by Many Agencies
 *   Each Agency has a number of ShelvingLocations (Although this relationship is seldom explicit in LMS implementations). Shelving locations are "Owned" by an Agency
 *
 * This entity allows us to assert policies at the shelving location level which can influence the handling of requests for items from that location.
 */
@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class ShelvingLocation {

    @NonNull
    @Id
    @TypeDef(type = DataType.UUID)
    private UUID id;

    @Nullable
    @DateCreated
    private Instant dateCreated;

    @Nullable
    @DateUpdated
    private Instant dateUpdated;

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
