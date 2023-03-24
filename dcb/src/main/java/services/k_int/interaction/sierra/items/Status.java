package services.k_int.interaction.sierra.items;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
@Serdeable
@Builder
@Data
public class Status {
	@Nullable
	final String code;
	@Nullable
	final String display;
	@Nullable
	final String duedate;
}
