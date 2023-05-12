package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Data
public class Block {
	@Nullable
	String code;
	@Nullable
	String until;
}
