package org.olf.dcb.request.lifecycle.ncip.profile.domain;

import static io.micronaut.data.model.DataType.JSON;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Serdeable
@MappedEntity("dcb_profile_membership")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Accessors(chain = true)
public class DcbProfileMembership {
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Version
	private Long version;

	private String profileId;
	private Integer profileVersion;

	@TypeDef(type = DataType.STRING)
	private DcbProfileMembershipState state;

	private String tokenHash;
	private Instant expiresAt;

	@TypeDef(type = JSON)
	@Builder.Default
	private Map<String, Object> policy = new LinkedHashMap<>();

	@Nullable
	private String remoteBaseUrl;
	@Nullable
	private String remoteDirectoryUrl;
	@Nullable
	private String remoteIssuer;
	@Nullable
	private String remoteSelfSlug;
	@Nullable
	private String selectedSymbol;
	@Nullable
	@TypeDef(type = DataType.UUID)
	private UUID hostLmsId;

	@Nullable
	@TypeDef(type = JSON)
	private Map<String, Object> approvedDescriptor;
	@Nullable
	private String approvedDescriptorHash;
	@Nullable
	@TypeDef(type = JSON)
	private Map<String, Object> pendingDescriptor;
	@Nullable
	private String pendingDescriptorHash;
	@Nullable
	private String idempotencyKey;
	@Nullable
	private String issuedBy;
	private Instant issuedAt;
	@Nullable
	private Instant redeemedAt;
	@Nullable
	private Instant revokedAt;
	@Nullable
	private Instant lastSyncedAt;
	@Nullable
	private Instant nextSyncAt;
	@Nullable
	private String lastSyncError;

	@Nullable
	@DateCreated
	private Instant dateCreated;
	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	public DcbProfileMembership setPolicy(Map<String, Object> policy) {
		this.policy = policy != null ? new LinkedHashMap<>(policy) : new LinkedHashMap<>();
		return this;
	}
}
