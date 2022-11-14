package services.k_int.gokb;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbIdentifier(
		String namespace,
		String value,
		String namespaceName) {
}
