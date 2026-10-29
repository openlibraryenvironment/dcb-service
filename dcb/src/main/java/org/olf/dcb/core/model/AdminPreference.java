package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.Map;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** One member of staff's preferences, under an opaque key: V9_0_020. */
@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class AdminPreference {
	@NonNull
	@Id
	private String adminKey;

	@Nullable
	private String themeMode;

	@Nullable
	private String textSize;

	@Nullable
	private String density;

	@Nullable
	private String fontName;

	@Nullable
	private String motion;

	@Nullable
	private String language;

	@NonNull
	@Builder.Default
	@TypeDef(type = DataType.JSON)
	private Map<String, Object> appState = Map.of();

	@NonNull
	private Instant dateCreated;

	@NonNull
	private Instant dateUpdated;

	@NonNull
	private Instant dateLastSeen;
}
