package services.k_int.interaction.sierra;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SierraCodeTuple (
        @NotEmpty String code,
        @NotEmpty String name
) {}
