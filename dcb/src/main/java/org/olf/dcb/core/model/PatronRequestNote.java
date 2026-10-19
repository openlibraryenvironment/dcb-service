package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * A note on a patron request. {@code patronRequestId} rather than an association: the table has
 * no foreign key onto patron_request.
 */
@Data
@Accessors(chain = true)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
public class PatronRequestNote {
	@ToString.Include
	@NonNull
	@NotNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@ToString.Include
	@NonNull
	@NotNull
	@TypeDef(type = DataType.UUID)
	private UUID patronRequestId;

	@ToString.Include
	@NonNull
	@NotNull
	private RequestNoteAuthorKind authorKind;

	/** Null exactly when the consortium wrote the note. */
	@ToString.Include
	@Nullable
	private String agencyCode;

	@Nullable
	private String body;

	@NonNull
	@NotNull
	private Instant dateCreated;

	@Nullable
	private Instant retractedAt;

	@Nullable
	private RequestNoteAuthorKind retractedBy;

	/** The authoring library's name, for display; filled by NoteLibraryNames, never stored. */
	@Transient
	@Nullable
	private String libraryName;

	@Transient
	public boolean isRetracted() {
		return retractedAt != null;
	}
}
