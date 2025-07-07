package org.olf.dcb.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;


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
		// or if there are no more suppliers possible, move to NO_ITEMS_SELECTABLE_AT_ANY_AGENCY
		NOT_SUPPLIED_CURRENT_SUPPLIER,
		NO_ITEMS_SELECTABLE_AT_ANY_AGENCY,
		REQUEST_PLACED_AT_SUPPLYING_AGENCY,
		// The supplying agency has confirmed the actual item which will be shipped
		CONFIRMED,
		// No further processing by DCB as this request should be handled by existing local (Same host/agency request) workflow.
		HANDED_OFF_AS_LOCAL,
		REQUEST_PLACED_AT_BORROWING_AGENCY,
		REQUEST_PLACED_AT_PICKUP_AGENCY,
		RECEIVED_AT_PICKUP,
		READY_FOR_PICKUP,
		LOANED, // Currently on loan
		PICKUP_TRANSIT, // In transit to pickup location
		RETURN_TRANSIT, // In transit back to owning location from lender
		CANCELLED,
		COMPLETED, // Everything is finished, regardless and ready to be finalised
		FINALISED, // We've cleaned up everything and this is the end of the line
		ERROR,
    ARCHIVED;

		private static final EnumMap<Status, Status> path = new EnumMap<>(Status.class);
		// expected path via https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/2870575137/Tracking+v3+matrix

		static {
			path.put(SUBMITTED_TO_DCB, PATRON_VERIFIED);
			path.put(PATRON_VERIFIED, RESOLVED);
			path.put(RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY);
			path.put(NOT_SUPPLIED_CURRENT_SUPPLIER, NOT_SUPPLIED_CURRENT_SUPPLIER);
			path.put(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY, NO_ITEMS_SELECTABLE_AT_ANY_AGENCY);
			path.put(REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED);
			path.put(CONFIRMED, REQUEST_PLACED_AT_BORROWING_AGENCY);
			path.put(REQUEST_PLACED_AT_BORROWING_AGENCY, PICKUP_TRANSIT);
			path.put(PICKUP_TRANSIT, RECEIVED_AT_PICKUP);
			path.put(RECEIVED_AT_PICKUP, READY_FOR_PICKUP);
			path.put(READY_FOR_PICKUP, LOANED);
			path.put(LOANED, RETURN_TRANSIT);
			path.put(RETURN_TRANSIT, COMPLETED);
			path.put(CANCELLED, CANCELLED);
			path.put(COMPLETED, FINALISED);
			path.put(FINALISED, FINALISED);
			path.put(ERROR, ERROR);
		}

		public Status getNextExpectedStatus() {
			return path.get(this);
		}
	}

	@Serdeable
	public enum RenewalStatus {
		ALLOWED,
		DISALLOWED,
		UNKNOWN,
		UNSUPPORTED
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

	// A shortcut for the patron's home identity host lms code
	// should be aligned with PlacePatronRequestCommand.requestor.localSystemCode
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

	@Nullable
	@Size(max = 200)
	private String pickupItemBarcode;

	// The unchanged item status within the pickup system
	@Nullable
	@Size(max = 200)
	private String rawPickupItemStatus;

	// When did we last poll for pickup item status (May only be used in 3-legged)
	@Nullable
	private Instant pickupItemLastCheckTimestamp;

	// How many times have we seen this pickupItemStatus when tracking? Used for backoff polling
	@Nullable
	private Long pickupItemStatusRepeat;

	// In order to hand the temporary item over the patron at the pickup
	// library, place a hold at the pickup lib and record it here
	@Nullable
	@Size(max = 200)
	private String pickupRequestId;

	// Track the state of the pickup hold here
	@Nullable
	@Size(max = 200)
	private String pickupRequestStatus;

	// The unchanged request status within the pickup system
	@Nullable
	@Size(max = 200)
	private String rawPickupRequestStatus;

	// When did we last poll for pickup request status (May only be used in 3-legged)
	@Nullable
	private Instant pickupRequestLastCheckTimestamp;

	// How many times have we seen this pickupRequestStatus when tracking? Used for backoff polling
	@Nullable
	private Long pickupRequestStatusRepeat;

	@Nullable
	@Size(max = 200)
	private String pickupBibId;

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
	private String rawLocalRequestStatus;

	// When did we last poll the local request
	@Nullable
	Instant localRequestLastCheckTimestamp;

	// How many times have we seen this localRequestStatus when tracking? Used for backoff polling
	@Nullable
	private Long localRequestStatusRepeat;

	@ToString.Include
	@Nullable
	private String localItemId;

	@ToString.Include
	@Nullable
	private String localItemHostlmsCode;

	@Nullable
	private String localItemAgencyCode;

	@ToString.Include
	@Nullable
	private Boolean isManuallySelectedItem;

	@Nullable
	private String localItemStatus;

	@Nullable
	private String rawLocalItemStatus;

	// When did we last poll the local item
	@Nullable
	Instant localItemLastCheckTimestamp;

	// How many times have we seen this localRequestStatus when tracking? Used for backoff polling
	@Nullable
	private Long localItemStatusRepeat;

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

	/**
	 * Set to true when a HostLMS Client has executed a local action that will prevent the patron from 
	 * renewing an item. A signal back to the owning institution that once the current loan has been completed the
	 * item should be returned.
	 */
	@Builder.Default
	@ToString.Include
	@Nullable
	private RenewalStatus renewalStatus = RenewalStatus.ALLOWED;

	@Nullable
	private Boolean isTooLong;


	@JsonProperty("status")
	public Status getStatus() {
		return this.status;
	}

	@JsonIgnore
	public PatronRequest setStatus(Status status) {

		// on a status change...
		if (this.status != status) {

			decidePreviousStatus();
			decideOutOfSequenceFlag(status);

			// keep the 'last' next expected status on error
			if (status != ERROR) {
				this.nextExpectedStatus = status.getNextExpectedStatus();
			}
		}

		this.status = status;
		return this;
	}

	private void decidePreviousStatus() {
		// set current status to previous
		if (this.status != null) {
			this.previousStatus = this.status;
		}
	}

	private void decideOutOfSequenceFlag(Status status) {
		// An ERROR status will always be treated as out of sequence
		if (this.status == Status.ERROR || status == Status.ERROR) {
			this.outOfSequenceFlag = Boolean.TRUE;
		}
		// first status change will not have a next expected status
		// if not null check the state change is what we expected
		else if (this.nextExpectedStatus != null && this.nextExpectedStatus != status) {
			this.outOfSequenceFlag = Boolean.TRUE;
		}
		else {
			this.outOfSequenceFlag = Boolean.FALSE;
		}
	}

	/**
	 * It is useful to have a shorthand note of the specific workflow which is in force for the patron request - initially
	 * RET- RETURNABLE ITEMS
	 * RET-LOCAL - We're placing a request in a single system - the patron, pickup and lending roles are all within a single system (1 Party)
	 * RET-STD - We're placing a request at a remote system, but the patron will pick the item up from their local library (2 parties)
	 * RET-PUA - The Borrower, Patron and Pickup systems are all different (3 parties)
	 * RET-EXP - We're placing a request where the supplier and the pickup systems are the same, but the patron may be external. This results in an expedited checkout. (2 parties).
	 */
	@Nullable
	private String activeWorkflow;

	@OneToMany(mappedBy = "patronRequest")
	private List<PatronRequestAudit> patronRequestAudits;

	@OneToMany(mappedBy = "patronRequest")
	private List<SupplierRequest> supplierRequests;

	// Is tracking this item paused
	@Nullable
	private Boolean isPaused;

	// Is this request flagged as needing administrative attention
	@Nullable
	private Boolean needsAttention;

	// Some implementations of the tracking service might scheduled polling in advance - this is the field
	// which can be used by those implementations
	@Nullable
	private Instant nextScheduledPoll;

	@Nullable
	private Integer autoPollCountForCurrentStatus;

	@Nullable
	private Integer manualPollCountForCurrentStatus;

	@Nullable
	private Integer pollCountForCurrentStatus;

	// When we go to ERROR this property allows us to know the previous state so that we can RETRY
	@JsonIgnore
	@ToString.Include
	@Nullable
	@Column(name = "previous_status_code") // Preserve the data mapping value from the old string type.
	private Status previousStatus;

	@Nullable
	private Instant currentStatusTimestamp;

	@Nullable
	private PatronRequest.Status nextExpectedStatus;

	@Nullable
	private Boolean outOfSequenceFlag;

	// in seconds
	@Nullable
	private Long elapsedTimeInCurrentStatus;

	// If we are able to tell, is the real item currently loaned to the VPatron. This can be used if the item
	// is returned to the lender, but then immediately re-loaned. In this scenario, item status is not sufficient
	// for us to infer that the loan has completed properly. Null if the host LMS is not able to tell us.
	@Nullable
	private Boolean isLoanedToPatron;

	@Nullable
	private Integer resolutionCount;

  @Builder.Default
	private Integer renewalCount = 0;

  @Builder.Default
	private Integer localRenewalCount = 0;

	@Nullable
	private Boolean isExpeditedCheckout;

	// Used in tracking item requests
	@ToString.Include
	@Nullable
	private String localHoldingId;

	@ToString.Include
	@Nullable
	private String pickupHoldingId;

	@Transient
	@Nullable
	public String determineSupplyingAgencyCode() {
		final List<SupplierRequest> supplierRequests = getValue(getSupplierRequests(), emptyList());

		return supplierRequests.stream()
			.findFirst()
			.map(SupplierRequest::getResolvedAgency)
			.map(agency -> getValueOrNull(agency, DataAgency::getCode))
			.orElse(null);
	}

	public PatronRequest resolve() {
		return setStatus(RESOLVED)
			.setResolutionCount(getValue(resolutionCount, 0) + 1);
	}

	public PatronRequest resolveToNoItemsSelectable() {
		log.debug("resolveToNoItemsAvailable()");

		return setStatus(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY);
	}

	public PatronRequest addLocalItemDetails(HostLmsItem hostLmsItem) {
		return setLocalItemId(hostLmsItem.getLocalId() != null ? hostLmsItem.getLocalId() : null)
			.setLocalItemStatus(hostLmsItem.getStatus() != null ? hostLmsItem.getStatus() : null)
			.setRawLocalItemStatus(hostLmsItem.getRawStatus() != null ? hostLmsItem.getRawStatus() : null)
			.setLocalHoldingId(hostLmsItem.getHoldingId() != null ? hostLmsItem.getHoldingId() : null);
	}

	public PatronRequest addPickupItemDetails(HostLmsItem hostLmsItem) {
		return setPickupItemId(hostLmsItem.getLocalId() != null ? hostLmsItem.getLocalId() : null)
			.setPickupItemStatus(hostLmsItem.getStatus() != null ? hostLmsItem.getStatus() : null)
			.setRawPickupItemStatus(hostLmsItem.getRawStatus() != null ? hostLmsItem.getRawStatus() : null)
			.setPickupHoldingId(hostLmsItem.getHoldingId() != null ? hostLmsItem.getHoldingId() : null);
	}


	public PatronRequest addManuallySelectedItemDetails(PlacePatronRequestCommand.Item item) {
		return setLocalItemId(item.getLocalId())
			.setLocalItemHostlmsCode(item.getLocalSystemCode())
			.setLocalItemAgencyCode(item.getAgencyCode())
			.setIsManuallySelectedItem(Boolean.TRUE);
	}

	public boolean getIsManuallySelectedItem() {
		return this.isManuallySelectedItem != null && this.isManuallySelectedItem;
	}

	public PatronRequest placedAtBorrowingAgency(LocalRequest localRequest) {
		return setLocalRequestId(localRequest.getLocalId())
			.setLocalRequestStatus(localRequest.getLocalStatus() != null ? localRequest.getLocalStatus() : null)
			.setRawLocalRequestStatus(localRequest.getRawLocalStatus() != null ? localRequest.getRawLocalStatus() : null)
			.setStatus(REQUEST_PLACED_AT_BORROWING_AGENCY);
	}

	public PatronRequest placedAtSupplyingAgency() {
		return setStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}

	public void incrementManualPollCountForCurrentStatus() {
		if (manualPollCountForCurrentStatus == null) {
			manualPollCountForCurrentStatus = 1;
		} else {
			manualPollCountForCurrentStatus++;
		}
		incrementCombinedPollCountForCurrentStatus();
	}

	public void incrementAutoPollCountForCurrentStatus() {
		if (autoPollCountForCurrentStatus == null) {
			autoPollCountForCurrentStatus = 1;
		} else {
			autoPollCountForCurrentStatus++;
		}
		incrementCombinedPollCountForCurrentStatus();
	}

	public void incrementCombinedPollCountForCurrentStatus() {
		if (pollCountForCurrentStatus == null) {
			pollCountForCurrentStatus = 1;
		} else {
			pollCountForCurrentStatus++;
		}
	}

	public PatronRequest placedAtPickupAgency(
		String localId, String localStatus,
		String rawLocalStatus, String requestedItemId,
		String requestedItemBarcode) {
		setPickupRequestId(localId);
		setPickupRequestStatus(localStatus);
		setRawPickupRequestStatus(rawLocalStatus);
		if (requestedItemId != null)
			setPickupItemId(requestedItemId);
		if (requestedItemBarcode != null)
			setPickupItemBarcode(requestedItemBarcode);
		setStatus(REQUEST_PLACED_AT_PICKUP_AGENCY);
		return this;
	}
}
