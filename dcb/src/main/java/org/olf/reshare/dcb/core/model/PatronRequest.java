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

	@Column(columnDefinition = "TEXT")
	private String patronAgencyCode;

	@Nullable
	@Column(columnDefinition = "TEXT")
	private String bibClusterId;

	@Nullable
	@Column(columnDefinition = "TEXT")
	private String pickupLocationCode;


	@NonNull
	public UUID getId() {
		return id;
	}

	public void setId(@NonNull UUID id) {
		this.id = id;
	}

	public String getPatronId() {
		return patronId;
	}

	public void setPatronId(String patronId) {
		this.patronId = patronId;
	}

	public String getPatronAgencyCode() {
		return patronAgencyCode;
	}

	public void setPatronAgencyCode(String patronAgencyCode) {
		this.patronAgencyCode = patronAgencyCode;
	}

	@Nullable
	public String getBibClusterId() {
		return bibClusterId;
	}

	public void setBibClusterId(@Nullable String bibClusterId) {
		this.bibClusterId = bibClusterId;
	}

	@Nullable
	public String getPickupLocationCode() {
		return pickupLocationCode;
	}

	public void setPickupLocationCode(@Nullable String pickupLocationCode) {
		this.pickupLocationCode = pickupLocationCode;
	}
}

