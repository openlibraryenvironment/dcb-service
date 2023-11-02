package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
@MappedEntity("event_log")
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
