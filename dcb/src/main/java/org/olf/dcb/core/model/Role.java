package org.olf.dcb.core.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.security.annotation.UpdatedBy;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.olf.dcb.core.audit.Auditable;

import java.util.UUID;

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class Role implements Auditable {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@TypeDef(type = DataType.STRING)
	private RoleName name;

	@NonNull
	@Size(max = 128)
	private String displayName;

	@Nullable
	@Size(max = 512)
	private String description;

	@Nullable
	@Size(max = 128)
	private String keycloakRole;

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
