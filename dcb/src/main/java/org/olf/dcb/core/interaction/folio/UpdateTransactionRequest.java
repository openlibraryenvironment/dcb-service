package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
public class UpdateTransactionRequest {
	Item item;

	@Data
	@Builder
	@Serdeable
	static class Item {
		String barcode;
		String materialType;
		String lendingLibraryCode;
	}
}
