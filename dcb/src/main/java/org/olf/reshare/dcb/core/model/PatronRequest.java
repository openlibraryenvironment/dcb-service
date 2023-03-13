package org.olf.reshare.dcb.core.model;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

import java.time.Instant;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class PatronRequest {

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@Nullable
	@Column
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@Column
	@DateUpdated
	private Instant dateUpdated;

	@Size(max = 200)
	private String patronId;

	@Size(max = 200)
	private String patronAgencyCode;

	@Nullable
	@Column(columnDefinition = "UUID")
	private UUID bibClusterId;

	@Nullable
	@Size(max = 200)
	private String pickupLocationCode;

	@Nullable
	@Size(max = 200)
	private String statusCode;

	public PatronRequest resolve() {
		return new PatronRequest(id, dateCreated, dateUpdated, patronId, patronAgencyCode, bibClusterId,
			pickupLocationCode, RESOLVED);
	}

}



