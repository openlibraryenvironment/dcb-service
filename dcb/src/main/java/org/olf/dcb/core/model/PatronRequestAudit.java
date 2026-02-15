package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder(access = AccessLevel.PUBLIC)
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class PatronRequestAudit {

	@NotNull
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@ToString.Exclude
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private PatronRequest patronRequest;

	@Nullable
	@DateCreated
	private Instant auditDate;

	@Nullable
	@Size(max = 256)
	private String briefDescription;

	@NonNull
	private PatronRequest.Status fromStatus;

	@NonNull
	private PatronRequest.Status toStatus;

	@Nullable
	@TypeDef(type = DataType.JSON)
	private Map<String, Object> auditData;
}
