package services.k_int.interaction.sierra.items;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
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
