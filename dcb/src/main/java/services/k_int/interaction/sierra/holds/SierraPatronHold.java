package services.k_int.interaction.sierra.holds;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import services.k_int.interaction.sierra.SierraCodeTuple;


@Builder
@Serdeable
public record SierraPatronHold(
	@NotEmpty String id,
	@Nullable String record,
	@Nullable String patron,
	@Nullable String frozen,
	@Nullable String placed,
	@Nullable String notWantedBeforeDate,
	@Nullable SierraCodeTuple pickupLocation,
	@Nullable SierraCodeTuple status,
	@Nullable String recordType,
	@Nullable String note
) {
}
