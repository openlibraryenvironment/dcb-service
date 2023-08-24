package services.k_int.interaction.sierra.configuration;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BranchResultSet(
        @Nullable int total,
        @Nullable int start,
        @NotNull List<BranchInfo> entries) {
}

