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
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class PatronRequest {

	@Creator
	public PatronRequest(UUID id, String patronId, String patronAgencyCode,
											 UUID bibClusterId, String pickupLocationCode
											 /*,@Nullable List<SupplierRequest> supplierRequests */) {

		this.id = id;
		this.patronId = patronId;
		this.patronAgencyCode = patronAgencyCode;
		this.bibClusterId = bibClusterId;
		this.pickupLocationCode = pickupLocationCode;
		
//		if (supplierRequests != null) {
//			this.supplierRequests = supplierRequests;
//		}
	}
//	
//	public PatronRequest(UUID id, String patronId, String patronAgencyCode,
//											 UUID bibClusterId, String pickupLocationCode) {
//		this(id,patronId,patronAgencyCode,bibClusterId,pickupLocationCode, null);
//	}

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private final UUID id;

	@Column(columnDefinition = "TEXT")
	private final String patronId;

	@Column(columnDefinition = "TEXT")
	private final String patronAgencyCode;

	@Nullable
	@Column(columnDefinition = "UUID")
	private final UUID bibClusterId;

	@Nullable
	@Column(columnDefinition = "TEXT")
	private final String pickupLocationCode;

	// Owned by other side.
//	@Nullable
//	@Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Cascade.ALL, mappedBy = "patronRequest")
//	private List<SupplierRequest> supplierRequests = new ArrayList<>();

	@NonNull
	public UUID getId() {
		return id;
	}

	public String getPatronId() {
		return patronId;
	}

	public String getPatronAgencyCode() {
		return patronAgencyCode;
	}

	@Nullable
	public UUID getBibClusterId() {
		return bibClusterId;
	}

	@Nullable
	public String getPickupLocationCode() {
		return pickupLocationCode;
	}

//	public void setSupplierRequests(List<SupplierRequest> supplierRequests) {
//		this.supplierRequests = supplierRequests;
//	}
//
//	public List<SupplierRequest> getSupplierRequests() {
//		return supplierRequests;
//	}

	public String toString() {
		return String.format("PatronRequest#%s",id);
	}
}

