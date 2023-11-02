package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Serdeable
@MappedEntity("event_log")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class Event {
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	UUID id;

	@Nullable
	@DateCreated
	Instant dateCreated;

	@Nullable
	@Column(name = "event_type")
	@TypeDef(type = DataType.STRING)
	EventType type;

	@Nullable
	@Column(name = "event_summary")
	String summary;
}
