package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import org.olf.dcb.core.audit.Auditable;
import org.olf.dcb.security.provisioning.LibraryUserStatus;
import org.olf.dcb.security.provisioning.ProvisionableRole;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * The binding between a DCB library and an account at the identity provider — never a
 * credential mirror. See {@code V9_0_006__library_user_account.sql} for what this table owns
 * and why it exists at all.
 *
 * <p>{@code libraryId} rather than a {@code Library} association, so a listing cannot pull
 * the whole library graph behind every row.
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
public class LibraryUserAccount implements Auditable {

	@ToString.Include
	@NonNull
	@NotNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	/** Which provider issued the account, so rows survive a provider migration legibly. */
	@NonNull
	@NotNull
	private String identityProvider;

	@ToString.Include
	@NonNull
	@NotNull
	private String identityProviderUserId;

	@NonNull
	@NotNull
	@TypeDef(type = DataType.UUID)
	private UUID libraryId;

	@ToString.Include
	@NonNull
	@NotNull
	private String agencyCode;

	@NonNull
	@NotNull
	private String email;

	@Nullable
	private String firstName;

	@Nullable
	private String lastName;

	/**
	 * Stored as its name. The Postgres CHECK constraint enforces the same two values, so
	 * a row can never say ADMIN however it was written.
	 */
	@NonNull
	@NotNull
	@TypeDef(type = DataType.STRING)
	private ProvisionableRole role;

	@NonNull
	@NotNull
	@TypeDef(type = DataType.STRING)
	private LibraryUserStatus status;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@UpdatedBy
	private String lastEditedBy;

	@Nullable
	private String reason;

	@Nullable
	private String changeCategory;

	@Nullable
	private String changeReferenceUrl;
}
