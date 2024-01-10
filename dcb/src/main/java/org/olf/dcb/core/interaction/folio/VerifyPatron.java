package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Serdeable
@Builder
@Data
public class VerifyPatron {
	@NonNull String id;
	@NonNull String pin;
}
