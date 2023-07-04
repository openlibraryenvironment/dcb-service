package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.*;

import java.time.Instant;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain=true)
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
        @Size(max=200)
        private String patronHostlmsCode;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private PatronIdentity requestingIdentity;

	@Nullable
	@TypeDef( type = DataType.UUID)
	private UUID bibClusterId;

	@Nullable
	@Size(max = 200)
	private String pickupLocationCode;

        // We may need to create a virtual patron at the pickup library as the item passes through. Record that here.
	@Nullable
	@Size(max = 200)
	private String pickupPatronId;

        // We may need to create a item patron at the pickup library. Record that here.
	@Nullable
	@Size(max = 200)
	private String pickupItemId;

        // track the status of an item created for the pickup hold
	@Nullable
	@Size(max = 200)
	private String pickupItemStatus;

        // In order to hand the temporary item over the the patron at the pickup library, place a hold at the pickup lib and record it here
	@Nullable
	@Size(max = 200)
	private String pickupRequestId;

        // Track the state of the pickup hold here
	@Nullable
	@Size(max = 200)
	private String pickupRequestStatus;

	@Nullable
	@Size(max = 200)
	private String statusCode;

	// Once we create a hold in the patrons home system, track it's ID here (Only unique in the context of the agencies host lms)
	@Nullable
	private String localRequestId;

	@Nullable
	private String localRequestStatus;

	@Nullable
	private String localItemId;

	@Nullable
	private String localBibId;

        @Nullable
        private String description;


	public PatronRequest resolve() {
		statusCode = RESOLVED;

		return this;
	}

	public PatronRequest resolveToNoItemsAvailable() {
		statusCode = NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;

		return this;
	}

	public PatronRequest placedAtBorrowingAgency(String localId, String localStatus) {
		localRequestId = localId;
		localRequestStatus = localStatus;
		statusCode = REQUEST_PLACED_AT_BORROWING_AGENCY;

		return this;
	}

	public PatronRequest placedAtSupplyingAgency() {
		statusCode = REQUEST_PLACED_AT_SUPPLYING_AGENCY;

		return this;
	}
}



