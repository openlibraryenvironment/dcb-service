package org.olf.dcb.core.interaction.folio;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Serdeable
@Builder
@Data
public class InventoryItemCollection {
	@Nullable List<InventoryItem> items;
}
