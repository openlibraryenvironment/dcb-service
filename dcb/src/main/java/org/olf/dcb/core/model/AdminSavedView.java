package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class AdminSavedView {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 64)
	private String adminKey;

	@NonNull
	@Size(max = 128)
	private String name;

	@ToString.Include
	@NonNull
	@Size(max = 48)
	private String viewType;

	@NonNull
	@TypeDef(type = DataType.JSON)
	private Map<String, Object> queryState;

	@NonNull
	private Instant dateCreated;

	@NonNull
	private Instant dateUpdated;
}
