package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;

@Serdeable
@Builder
@Data
public class VerifyPatron {
	@NonNull String id;
	// Kept out of toString, which is what an error's request body shows
	@ToString.Exclude
	@NonNull String pin;
}
