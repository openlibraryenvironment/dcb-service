package org.olf.dcb.core.interaction.folio;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class InventoryItem {
	@Nullable private String id;
	@Nullable private String barcode;
	@Nullable private String holdingsRecordId;
	@Nullable private InventoryItemStatus status;
}
