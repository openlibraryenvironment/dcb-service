package services.k_int.interaction.sierra.patrons;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Data
@Builder
public class Block {
	@Nullable
	String code;
	@Nullable
	String until;
}
