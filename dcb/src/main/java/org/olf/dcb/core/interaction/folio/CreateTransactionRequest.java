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
	Patron patron;
	Pickup pickup;

	@Data
	@Builder
	@Serdeable
	static class Item {
		String id;
		String barcode;
	}

	@Data
	@Builder
	@Serdeable
	static class Patron {
		String id;
		String barcode;
		String group;
	}

	@Data
	@Builder
	@Serdeable
	static class Pickup {
		String servicePointId;
		String servicePointName;
		String libraryCode;
	}
}
