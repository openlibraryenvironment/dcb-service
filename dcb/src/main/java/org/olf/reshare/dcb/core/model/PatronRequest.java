package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class PatronRequest {
	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;
	
	@Column(columnDefinition = "TEXT")
	private String patronId;

        @Nullable
	@Column(columnDefinition = "TEXT")
	private String bibClusterId;

        @Nullable
	@Column(columnDefinition = "TEXT")
	private String pickupLocationCode;
}

