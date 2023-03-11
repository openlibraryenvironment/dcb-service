package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@Data
@MappedEntity
@RequiredArgsConstructor(onConstructor_ = @Creator())
// @AllArgsConstructor
@Builder
public class PatronRequest {

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private final UUID id;

	@Size(max = 200)
	private final String patronId;

	@Size(max = 200)
	private final String patronAgencyCode;

	@Nullable
	@Column(columnDefinition = "UUID")
	private final UUID bibClusterId;


	@Nullable
	@Size(max = 200)
	private final String pickupLocationCode;

	@Nullable
	@Size(max = 200)
	private final String statusCode;

	public PatronRequest resolve() {
		return new PatronRequest(id, patronId, patronAgencyCode, bibClusterId,
			pickupLocationCode, RESOLVED);
	}

}



