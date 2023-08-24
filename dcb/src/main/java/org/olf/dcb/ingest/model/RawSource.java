package org.olf.dcb.ingest.model;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
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

@Data
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class RawSource {
	
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;
	
	@NotNull
	@NonNull
	private UUID hostLmsId;
	
	@NotNull
	@NonNull
	@Size(max = 255)
	private String remoteId;

	@NotNull
	@NonNull
	@TypeDef(type = DataType.JSON)
	private Map<String,?> json;
}
