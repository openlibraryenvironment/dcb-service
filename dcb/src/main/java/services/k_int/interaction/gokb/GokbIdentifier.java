package services.k_int.interaction.gokb;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbIdentifier(
		String namespace,
		String value) {
}
