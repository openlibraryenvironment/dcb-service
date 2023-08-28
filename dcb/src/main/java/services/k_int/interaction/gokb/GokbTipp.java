package services.k_int.interaction.gokb;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbTipp(
	@NotNull UUID uuid,
	@Nullable String tippTitleName,
	@Nullable String titleType,
	List<GokbIdentifier> identifiers) {
}
