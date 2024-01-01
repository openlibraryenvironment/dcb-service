package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


/**
 * A patron is the canonical record that links together all the different patron
 * identities for a user across the network.
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class Patron {

	@NotNull
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@ToString.Exclude
	@Nullable
	@DateCreated
	private Instant dateCreated;

	@ToString.Exclude
	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Size(max = 200)
	private String homeLibraryCode;

	@ToString.Exclude
	@OneToMany(mappedBy = "patronId")
	private List<PatronIdentity> patronIdentities;
}
