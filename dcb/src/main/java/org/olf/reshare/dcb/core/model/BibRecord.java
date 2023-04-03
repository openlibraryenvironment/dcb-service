package org.olf.reshare.dcb.core.model;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;

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
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain = true)
public class BibRecord {

	@NotNull
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

	@NotNull
	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID sourceSystemId;

	@NotNull
	@NonNull
	@Size(max = 256)
	private String sourceRecordId;

	@Nullable
	@TypeDef(type = DataType.STRING)
	private String title;
	
	@Nullable
	// private UUID contributesTo;
        @Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "contributes_to")
	private ClusterRecord contributesTo;

	@Nullable
	private String recordStatus;

	@Nullable
	private String typeOfRecord;
}
