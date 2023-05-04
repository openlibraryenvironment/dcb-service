package services.k_int.interaction.sierra.holds;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SierraPatronHold (
        @NotEmpty String id
        ) {}
