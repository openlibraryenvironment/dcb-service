package org.olf.reshare.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import java.util.Set;

@Builder
@Data
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class ClusterRecord {
	
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private final UUID id;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@TypeDef(type = DataType.STRING)
	private String title;

	@Nullable
	@Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy="contributesTo")
	private Set<BibRecord> bibs;
}
