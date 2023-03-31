package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@Data
@MappedEntity
@RequiredArgsConstructor(onConstructor_ = @Creator())
// @AllArgsConstructor
@Builder
public class SupplierRequest {

	@NotNull
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private final UUID id;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "patron_request_id")
	private final PatronRequest patronRequest;

	@NotNull
	@NonNull
	@Size(max = 200)
	private final String itemId;

	@NotNull
	@NonNull
	@Size(max = 200)
	private final String hostLmsCode;
}

