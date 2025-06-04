package org.olf.dcb.dataimport.job.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import services.k_int.utils.UUIDUtils;

@Data
@Builder(toBuilder = true)
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class SourceRecord {
	
	public static enum ProcessingStatus {
		PROCESSING_REQUIRED,
		SUCCESS,
		FAILURE
	}

	@Id
	@NonNull
	@NotNull
	@TypeDef( type = DataType.UUID )
	private UUID id;

  // Extracted to this method so we can call this method should we need to regenerate the id elsewhere
  public static UUID generateSourceRecordId(UUID hstId, String recId) {
    final UUID ns = UUIDUtils.nameUUIDFromNamespaceAndString(hstId, "SourceRecord");
    return UUIDUtils.nameUUIDFromNamespaceAndString(ns, recId);
  }
	
	public UUID getId() {
		if (id == null)
			id = generateSourceRecordId(hostLmsId,remoteId);
		
		return this.id;
	}
	
	@NonNull
	@NotNull
	@TypeDef( type = DataType.UUID )
	private final UUID hostLmsId;
	
	@NonNull
	@NotNull
	private final String remoteId;

	@NonNull
	@NotNull
	private final Instant lastFetched;
	
	@Nullable
	private final Instant lastProcessed;

  @Builder.Default
	@Nullable
  private ProcessingStatus processingState = ProcessingStatus.PROCESSING_REQUIRED;

	@Nullable
	private final String processingInformation;
	
	@Nullable
	@TypeDef(type = DataType.JSON)
	@ToString.Exclude
	private final JsonNode sourceRecordData;
	
}
