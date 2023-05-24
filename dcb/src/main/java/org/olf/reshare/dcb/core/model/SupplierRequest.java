package org.olf.reshare.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.olf.reshare.dcb.request.fulfilment.SupplierRequestStatusCode;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;


import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.UUID;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@Data
@MappedEntity
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class SupplierRequest {
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "patron_request_id")
	private PatronRequest patronRequest;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String localItemId;

	@Nullable
	@Size(max = 200)
	private String localItemBarcode;

	@Nullable
	@Size(max = 200)
	private String localItemLocationCode;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String hostLmsCode;

	@Nullable
	@Size(max = 200)
	@Enumerated(EnumType.STRING)
	private SupplierRequestStatusCode statusCode;

	// Once we have placed a hold at the lending system, track that hold by storring it's ID here
	// this will only be unique within the context of a hostLmsCode
	@Nullable
	@Size(max = 200)
	private String localId;

	@Nullable
	@Size(max = 32)
	private String localStatus;
}
