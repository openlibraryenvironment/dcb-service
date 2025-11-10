package org.olf.dcb.core.interaction.folio;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
class CreateTransactionRequest {
	String role;
	Boolean selfBorrowing;
	Item item;
	Patron patron;
	Pickup pickup;

	@Data
	@Builder
	@Serdeable
	static class Item {
		String id;
		String barcode;
		String title;
		String materialType;
		String lendingLibraryCode;
	}

	@Data
	@Builder
	@Serdeable
	static class Patron {
		String id;
		String barcode;
		String group;
		String localNames;
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
