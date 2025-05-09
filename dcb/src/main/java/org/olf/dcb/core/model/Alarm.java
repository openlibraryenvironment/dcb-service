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
import lombok.Singular;
import org.olf.dcb.core.audit.Auditable;
import java.time.Instant;

import java.util.UUID;
import java.util.Map;


// ToDo : Periodic checks / audit alarms for Libraries where agency is not present

@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class Alarm {

	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	private String code;

	private Instant created;

	private Instant lastSeen;

	private Long repeatCount;

	private Instant expires;

  @NonNull
  @Singular("alarmDetail")
  @TypeDef(type = DataType.JSON)
  Map<String, Object> alarmDetails;

	public void incrementRepeatCount() {
		repeatCount = Long.valueOf(repeatCount.longValue()+1);
	}
}
