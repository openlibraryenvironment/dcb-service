package org.olf.dcb.core.interaction.folio;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class OuterHolding {
	@Nullable String instanceId;
	List<Holding> holdings;
}
