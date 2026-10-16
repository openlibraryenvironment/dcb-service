package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.security.annotation.UpdatedBy;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.olf.dcb.core.audit.Auditable;

/**
 * Something a library, a group or the consortium needs to tell its patrons, scoped by
 * {@link ScopeLevel}. It always expires, its body is plain text, and dismissal is remembered
 * by the discovery service rather than here. Why: docs/discovery-service-approach.md §6.4,
 * "Announcements".
 */
@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity("announcement")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class Announcement implements Auditable {

	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@ToString.Include
	@NonNull
	@TypeDef(type = DataType.STRING)
	private ScopeLevel scopeType;

	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID scopeId;

	@ToString.Include
	@NonNull
	@NotNull
	@Size(max = 200)
	private String headline;

	@NonNull
	@NotNull
	@Size(max = 1000)
	private String body;

	/**
	 * Only an urgent CONSORTIUM announcement shows beneath a library's own. Everything else
	 * is one at a time, narrowest wins — two banners with no hierarchy is the thing that
	 * rule exists to prevent.
	 */
	@NonNull
	@Builder.Default
	private Boolean urgent = false;

	/** An outage notice usually should not be; "a new source is available" should. */
	@NonNull
	@Builder.Default
	private Boolean dismissible = true;

	/** Lets an operator write Monday's notice on Friday. */
	@NonNull
	@NotNull
	private Instant startsAt;

	@NonNull
	@NotNull
	private Instant expiresAt;

	@Nullable
	@UpdatedBy
	private String lastEditedBy;

	@Nullable
	@Size(max = 100)
	private String reason;

	@Nullable
	@Size(max = 200)
	private String changeReferenceUrl;

	@Nullable
	@Size(max = 200)
	private String changeCategory;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;
}
