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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Serdeable
@MappedEntity(value = "data_change_log")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DataChangeLog {
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID entityId;

	@NonNull
	@Size(max = 100)
	private String entityType;

	@NonNull
	@Size(max = 50)
	private String actionInfo;

	@Nullable
	private String lastEditedBy;

	@NonNull
	private Instant timestampLogged;

	@Nullable
	@Size(max = 500)
	private String reason;

	@Nullable
	@Size(max = 200)
	private String changeReferenceUrl;

	@Nullable
	@Size(max = 100)
	private String changeCategory;

	@NonNull
	@TypeDef(type = DataType.JSON)
	private String changes;
}
