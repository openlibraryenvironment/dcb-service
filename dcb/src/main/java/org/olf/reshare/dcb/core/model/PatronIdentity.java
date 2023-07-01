package org.olf.reshare.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import javax.validation.constraints.NotNull;

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
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class PatronIdentity {
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

	@ToString.Exclude
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostLms;

	@NotNull
	@NonNull
	private String localId;

	@NotNull
	@NonNull
	private Boolean homeIdentity;

	@Nullable
	private String localBarcode;

	// PII: The system may allow administrators to set local policies which control what values
	// can appear in this field. This field is not forced to be populated.
	@Nullable
	private String localNames;

	@Nullable
	private String localPtype;

	@Nullable
	private String localAgency;

	@Nullable
	private Instant lastValidated;
}
