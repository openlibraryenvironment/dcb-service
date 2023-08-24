package services.k_int.interaction.sierra;

import io.micronaut.serde.annotation.Serdeable;

import jakarta.validation.constraints.NotEmpty;

@Serdeable
public record SierraCodeTuple (
        @NotEmpty String code,
        @NotEmpty String name
) {}
