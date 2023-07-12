package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
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
	@Size(max = 200)
	private String fromStatus;

	@NonNull
	@Size(max = 200)
	private String toStatus;

	@Nullable
	@TypeDef(type = DataType.JSON)
	Map<String, Object> auditData;
}
