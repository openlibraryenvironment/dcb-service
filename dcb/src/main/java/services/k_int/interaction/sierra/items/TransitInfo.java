package services.k_int.interaction.sierra.items;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class TransitInfo {
	@Nullable
	Location to;
	Boolean forHold;
}
