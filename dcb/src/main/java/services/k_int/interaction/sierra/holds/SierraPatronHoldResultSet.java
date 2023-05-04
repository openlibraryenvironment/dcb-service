package services.k_int.interaction.sierra.holds;

import java.util.List;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SierraPatronHoldResultSet(
        @Nullable int total,
        @Nullable int start,
        @NotNull List<SierraPatronHold> entries) {
}

