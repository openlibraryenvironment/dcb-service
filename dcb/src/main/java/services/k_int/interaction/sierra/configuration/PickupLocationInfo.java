package services.k_int.interaction.sierra.configuration;

import javax.validation.constraints.NotEmpty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;

@Serdeable
public record PickupLocationInfo(
	@Getter @NotEmpty String code,
	@Getter @NotEmpty String name) { }
