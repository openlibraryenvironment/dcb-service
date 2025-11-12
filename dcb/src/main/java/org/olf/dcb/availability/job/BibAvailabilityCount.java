package org.olf.dcb.availability.job;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@MappedEntity
@RequiredArgsConstructor
@Builder(toBuilder = true)
@ExcludeFromGeneratedCoverageReport
public class BibAvailabilityCount {
	
	public static enum Status {
		RECHECK_REQUIRED,
		UNMAPPED,
		MAPPED
	}

	@Id
	@NotNull
	@NonNull
	@TypeDef(type = DataType.UUID)
	private final UUID id;
	
	@NotNull
	@NonNull
	private final UUID bibId;

	@NotNull
	@NonNull
	private final UUID hostLms;

	@Nullable
	private final String remoteLocationCode;

	@Nullable
	private final String internalLocationCode;
	
	@Min(0)
	private final int count;
	
	@NonNull
	@NotNull
	private final Status status;

	@Nullable
	private final String mappingResult;
	
	@NonNull
	@NotNull
	private final Instant lastUpdated;

	@NonNull
	@NotNull
	private final Instant gracePeriodEnd;
}
