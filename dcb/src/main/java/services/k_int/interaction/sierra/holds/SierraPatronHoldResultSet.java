package services.k_int.interaction.sierra.holds;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;


@Serdeable
public record SierraPatronHoldResultSet(
        @Nullable int total,
        @Nullable int start,
        @NotNull List<SierraPatronHold> entries) {
}

