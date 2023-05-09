package org.olf.reshare.dcb.core.model;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

import java.time.Instant;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
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
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@Nullable
	@TypeDef( type = DataType.UUID)
	private UUID bibClusterId;

	@Nullable
	@Size(max = 200)
	private String pickupLocationCode;

	@Nullable
	@Size(max = 200)
	private String statusCode;

	// Once we create a hold in the patrons home system, track it's ID here (Only unique in the context of the agencies host lms)
	@Nullable
	private String localSystemHoldId;

	public PatronRequest resolve() {
		return new PatronRequest(id, dateCreated, dateUpdated, patron,
			bibClusterId, pickupLocationCode, RESOLVED, localSystemHoldId);
	}

	public PatronRequest resolveToNoItemsAvailable() {
		return new PatronRequest(id, dateCreated, dateUpdated, patron,
			bibClusterId, pickupLocationCode,
			NO_ITEMS_AVAILABLE_AT_ANY_AGENCY, localSystemHoldId);
	}
}



