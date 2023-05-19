package services.k_int.interaction.sierra;

import javax.validation.constraints.NotEmpty;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SierraCodeTuple (
        @NotEmpty String code,
        @NotEmpty String name
) {}
