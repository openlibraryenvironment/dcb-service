package services.k_int.interaction.alma.types;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

// Kept apart from AlmaUser, which is logged and written back to Alma on update
@Serdeable
public record AlmaUserPin(@Nullable String pin_number) {
	@Override
	public String toString() {
		return "AlmaUserPin[pin_number=<redacted>]";
	}
}
