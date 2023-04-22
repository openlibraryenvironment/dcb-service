package services.k_int.interaction.sierra.configuration;

import io.micronaut.serde.annotation.Serdeable;
import javax.validation.constraints.NotEmpty;

@Serdeable
public record PickupLocationInfo(
        @NotEmpty String code,
        @NotEmpty String name
) {

}