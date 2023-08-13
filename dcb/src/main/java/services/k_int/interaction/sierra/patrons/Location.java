package services.k_int.interaction.sierra.patrons;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class Location {
	@Nullable
	String code;
	@Nullable
	String name;
}
