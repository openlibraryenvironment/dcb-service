package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class Address {
	@Nullable
	String[] lines;
	@Nullable
	Character type;
}
