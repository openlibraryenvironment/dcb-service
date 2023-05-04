package services.k_int.interaction.sierra.holds;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import services.k_int.interaction.sierra.SierraCodeTuple;

@Serdeable
public record SierraPatronHold (
        @NotEmpty String id,
        @Nullable String record,
        @Nullable String patron,
        @Nullable String frozen,
        @Nullable String placed,
        @Nullable String notWantedBeforeDate,
        @Nullable SierraCodeTuple pickupLocation,
        @Nullable SierraCodeTuple status,
        @Nullable String recordType
        ) {}
