package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class SupplierRequest {
	public SupplierRequest(@NonNull UUID id, @Nullable PatronRequest patronRequest,
		@NonNull UUID holdingsItemId, @NonNull String holdingsAgencyCode) {

		this.id = id;
		this.patronRequest = patronRequest;
		this.HoldingsItemId = holdingsItemId;
		this.HoldingsAgencyCode = holdingsAgencyCode;
	}

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private final UUID id;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "patron_request_id")
	private final PatronRequest patronRequest;

	@NotNull
	@NonNull
	@Column(columnDefinition = "UUID")
	private final UUID HoldingsItemId;

	@NotNull
	@NonNull
	@Column(columnDefinition = "TEXT")
	private final String HoldingsAgencyCode;


	@NonNull
	public UUID getId() {
		return id;
	}

	@NonNull
	public UUID getHoldingsItemId() {
		return HoldingsItemId;
	}

	@NonNull
	public String getHoldingsAgencyCode() {
		return HoldingsAgencyCode;
	}

	public PatronRequest getPatronRequest() {
		return patronRequest;
	}

	@Override
	public String toString() {
		return "SupplierRequest{" +
			"id=" + id +
			", patronRequest=" + patronRequest +
			", HoldingsItemId=" + HoldingsItemId +
			", HoldingsAgencyCode='" + HoldingsAgencyCode + '\'' +
			'}';
	}
}

