package org.olf.dcb.core.interaction.folio;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class OuterHoldings {
	 List<OuterHolding> holdings;
}
