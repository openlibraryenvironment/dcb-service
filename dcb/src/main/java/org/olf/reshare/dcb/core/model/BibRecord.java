package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import java.time.LocalDateTime;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

@Builder
@Data
@NoArgsConstructor(onConstructor_=@Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class BibRecord {
	
	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@Nullable
	@Column
        @DateCreated
	private LocalDateTime dateCreated;

	@Nullable
	@Column
        @DateUpdated
	private LocalDateTime dateUpdated;

	@Nullable
	@Column(columnDefinition = "TEXT")
	private String title;
}
