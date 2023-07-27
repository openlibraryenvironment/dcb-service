package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain = true)
public class PatronRequest {

	@Serdeable
	public static enum Status {
		SUBMITTED_TO_DCB, PATRON_VERIFIED, RESOLVED, NO_ITEMS_AVAILABLE_AT_ANY_AGENCY, REQUEST_PLACED_AT_SUPPLYING_AGENCY,
		REQUEST_PLACED_AT_BORROWING_AGENCY, CANCELLED, ERROR
	}

	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@ToString.Exclude
	@Nullable
	@DateCreated
	private Instant dateCreated;

	@ToString.Exclude
	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Size(max = 200)
	private String patronHostlmsCode;

	@ToString.Exclude
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@ToString.Exclude
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private PatronIdentity requestingIdentity;

	@Nullable
	@TypeDef(type = DataType.UUID)
	private UUID bibClusterId;

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

	// track the status of an item created for the pickup hold
	@Nullable
	@Size(max = 200)
	private String pickupItemStatus;

	// In order to hand the temporary item over the the patron at the pickup
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
	// field but allow deserilization for output.
	@JsonIgnore
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
	private String localBibId;

	@Nullable
	private String description;

	@Nullable
	private String errorMessage;
	
	@JsonProperty("status")
	public Status getStatus() {
		return this.status;
	}
	
	@JsonIgnore
	public PatronRequest setStatus( Status status ) {
		this.status = status;
		return this;
	}

	@ToString.Exclude
	@OneToMany(mappedBy = "patronRequestAuditId")
	private List<PatronRequestAudit> patronRequestAudits;

	public PatronRequest resolve() {
		return setStatus(Status.RESOLVED);
	}

	public PatronRequest resolveToNoItemsAvailable() {
		return setStatus(Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY);
	}

	public PatronRequest placedAtBorrowingAgency(String localId, String localStatus) {
		return setLocalRequestId(localId).setLocalRequestStatus(localStatus)
				.setStatus(Status.REQUEST_PLACED_AT_BORROWING_AGENCY);
	}

	public PatronRequest placedAtSupplyingAgency() {
		return setStatus(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}
}
