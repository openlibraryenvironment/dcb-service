package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
class CreateTransactionRequest {
	String role;
	Item item;

	@Data
	@Builder
	@Serdeable
	static class Item {
		String id;
		String barcode;
	}
}
