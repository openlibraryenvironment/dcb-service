package org.olf.dcb.core.interaction.folio;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Serdeable
@Value
@Builder
public class MaterialType {
	@Nullable String name;
}
