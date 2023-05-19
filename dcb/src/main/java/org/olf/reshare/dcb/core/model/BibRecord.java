package org.olf.reshare.dcb.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.olf.reshare.dcb.core.model.clustering.ClusterRecord;

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
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder(toBuilder = true)
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
	
	// might have to think about adding serialize = false to @Relation to prevent cycles
//	@NonNull
//	@NotNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "contributes_to")
	private ClusterRecord contributesTo;

	// A note about why we made the clustering decision we made
	@Nullable
	private String clusterReason;

	@Nullable
	private String recordStatus;

	@Nullable
	private String typeOfRecord;

	@Nullable
	private String derivedType;

	// Generate a string which might be useful in blocking titles
	// for stage one of deduplication
	@Nullable
	private String blockingTitle;

	@Nullable
	@TypeDef(type = DataType.JSON)
	Map<String, Object> canonicalMetadata;

        // Allocate a score to this record indicating it's semantic density in order to choose the
        // best member of a cluster for presentation to the user
	@Nullable
        Integer metadataScore;

        // When we process a source record to produce a bibRecord we can now track what version of the 
        // process was applied. This gives us a way to know which records need to be reprocessed when we
        // updgrade a system.
	@Nullable
        Integer processVersion;
}
