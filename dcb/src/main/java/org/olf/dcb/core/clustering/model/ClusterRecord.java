package org.olf.dcb.core.clustering.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain = true)
@ToString(exclude = "bibs" )
public class ClusterRecord {
	
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

  @JsonIgnoreProperties({"contributesTo"})
	@Nullable
	@Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy="contributesTo")
	private Set<BibRecord> bibs;

	// The UUID of the bib record selected to "Represent" this cluster (could be the first record)
	@Nullable
	private UUID selectedBib;

	@Nullable
	private Boolean isDeleted;
}
