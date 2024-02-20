package org.olf.dcb.core.model;

import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Slf4j
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
public class PatronRequest {
	@Serdeable
	public enum Status {
		SUBMITTED_TO_DCB, 
		PATRON_VERIFIED, 
		RESOLVED, 

		// Added in preparation for moving to next supplier - when a supplier cancels a request we
		// want to resubmit the request to the next possible supplier, creating a new supplier_request
		// or if there are no more suppliers possible, move to NO_ITEMS_AVAILABLE_AT_ANY_AGENCY
		NOT_SUPPLIED_CURRENT_SUPPLIER,
		NO_ITEMS_AVAILABLE_AT_ANY_AGENCY,
		REQUEST_PLACED_AT_SUPPLYING_AGENCY,
		REQUEST_PLACED_AT_BORROWING_AGENCY,
		RECEIVED_AT_PICKUP,
		READY_FOR_PICKUP,
		LOANED, // Currently on loan
		PICKUP_TRANSIT, // In transit to pickup location
		RETURN_TRANSIT, // In transit back to owning location from lender
		CANCELLED,
		COMPLETED, // Everything is finished, regardless and ready to be finalised
		FINALISED, // We've cleaned up everything and this is the end of the line
		ERROR
	}

	@ToString.Include
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

	@Nullable
	@Size(max = 200)
	private String patronHostlmsCode;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private PatronIdentity requestingIdentity;

	@Nullable
	@TypeDef(type = DataType.UUID)
	private UUID bibClusterId;

	@Nullable
	@Size(max = 32)
	private String requestedVolumeDesignation;

	// The naming authority which disambiguates the pickupLocationCode - most commonly at the moment
	// the HostLMS system ID, but feasibly ISIL identifiers or other globally scoped unique values
	@Nullable
	@Size(max = 200)
	private String pickupLocationCodeContext;

	@Nullable
	@Size(max = 200)
	private String pickupLocationContext;

	@Nullable
	@Size(max = 200)
	private String pickupLocationCode;

	// We may need to create a virtual patron at the pickup library as the item
	// passes through. Record that here.
	@Nullable
	@Size(max = 200)
	private String pickupPatronId;

	// We may need to create a item patron at the pickup library. Record that here.
	@Nullable
	@Size(max = 200)
	private String pickupItemId;

	@Nullable
	@Size(max = 32)
	private String pickupItemType;

	// track the status of an item created for the pickup hold
	@Nullable
	@Size(max = 200)
	private String pickupItemStatus;

	// In order to hand the temporary item over the patron at the pickup
	// library, place a hold at the pickup lib and record it here
	@Nullable
	@Size(max = 200)
	private String pickupRequestId;

	// Track the state of the pickup hold here
	@Nullable
	@Size(max = 200)
	private String pickupRequestStatus;

	// Ignore at this property level. We provide explicit ignore/serializing
	// instructions at a getter and setter level to prevent any JSON binding to this
	// field but allow deserialization for output.
	@JsonIgnore
	@ToString.Include
	@Nullable
	@Column(name = "status_code") // Preserve the data mapping value from the old string type.
	private Status status;

	// Once we create a hold in the patrons home system, track it's ID here (Only
	// unique in the context of the agencies host lms)
	@Nullable
	private String localRequestId;

	@Nullable
	private String localRequestStatus;

	@Nullable
	private String localItemId;

	@Nullable
	private String localItemStatus;

	@Nullable
	@Size(max = 32)
	private String localItemType;

	@Nullable
	private String localBibId;

	@ToString.Include
	@Nullable
	private String description;

	@Nullable
	private String errorMessage;
	
	@Nullable
	private String protocol;
	
	@Nullable
	private String requesterNote;
	
	@JsonProperty("status")
	public Status getStatus() {
		return this.status;
	}
	
	@JsonIgnore
	public PatronRequest setStatus(Status status) {
		this.status = status;
		return this;
	}

	/**
	 * It is useful to have a shorthand note of the specific workflow which is in force for the patron request - initially
	 * RET- RETURNABLE ITEMS
	 * RET-LOCAL - We're placing a request in a single system - the patron, pickup and lending roles are all within a single system (1 Party)
	 * RET-STD - We're placing a request at a remote system, but the patron will pick the item up from their local library (2 parties)
	 * RET-PUA - The Borrower, Patron and Pickup systems are all different (3 parties)
	 */
	@Nullable
	private String activeWorkflow;

	@OneToMany(mappedBy = "patronRequest")
	private List<PatronRequestAudit> patronRequestAudits;

	@OneToMany(mappedBy = "patronRequest")
	private List<SupplierRequest> supplierRequests;

	public PatronRequest resolve() {
		return setStatus(RESOLVED);
	}

	public PatronRequest resolveToNoItemsAvailable() {
		log.debug("resolveToNoItemsAvailable()");

		return setStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);
	}

	public PatronRequest placedAtBorrowingAgency(String localId, String localStatus) {
		return setLocalRequestId(localId)
			.setLocalRequestStatus(localStatus)
			.setStatus(REQUEST_PLACED_AT_BORROWING_AGENCY);
	}

	public PatronRequest placedAtSupplyingAgency() {
		return setStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}
}
