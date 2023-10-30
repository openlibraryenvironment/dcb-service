package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

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
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


/**
 * The identifier for a patron in a specific host system.
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@Accessors(chain = true)
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@ToString(onlyExplicitlyIncluded = true)
public class PatronIdentity {

	@ToString.Include
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

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostLms;

	@ToString.Include
	@NotNull
	@NonNull
	private String localId;

	@ToString.Include
	@NotNull
	@NonNull
	private Boolean homeIdentity;

	@Nullable
	private String localBarcode;

	// PII: The system may allow administrators to set local policies which control what values
	// can appear in this field. This field is not forced to be populated.
	@Nullable
	private String localNames;

	@ToString.Include
	@Nullable
	private String localPtype;

	@ToString.Include
	@Nullable
	private String canonicalPtype;

	@Nullable
	private String localHomeLibraryCode;

	@Nullable
	private Instant lastValidated;

	@Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency resolvedAgency;

}
