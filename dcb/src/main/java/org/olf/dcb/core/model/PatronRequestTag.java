package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/** One author's application of a tag to a patron request. */
@Data
@Accessors(chain = true)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@MappedEntity
public class PatronRequestTag {
	@NonNull
	@NotNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@NotNull
	@TypeDef(type = DataType.UUID)
	private UUID patronRequestId;

	@NonNull
	@NotNull
	private String tagCode;

	@NonNull
	@NotNull
	private RequestNoteAuthorKind authorKind;

	/** Null exactly when the consortium applied the tag. */
	@Nullable
	private String agencyCode;

	@NonNull
	@NotNull
	private Instant dateCreated;
}
