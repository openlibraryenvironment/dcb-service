package services.k_int.interaction.sierra.configuration;

import javax.validation.constraints.NotEmpty;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PickupLocationInfo(
        @NotEmpty String code,
        @NotEmpty String name
) {

}