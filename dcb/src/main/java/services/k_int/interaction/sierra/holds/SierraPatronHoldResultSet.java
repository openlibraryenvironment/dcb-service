package services.k_int.interaction.sierra.holds;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;


@Serdeable
@Builder
public record SierraPatronHoldResultSet(
	@Nullable int total,
	@Nullable int start,
	@NotNull List<SierraPatronHold> entries) {
}

