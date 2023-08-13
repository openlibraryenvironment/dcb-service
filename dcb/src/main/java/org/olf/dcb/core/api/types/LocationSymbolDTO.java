package org.olf.dcb.core.api.types;

import java.util.UUID;

import org.olf.dcb.core.model.LocationSymbol;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LocationSymbolDTO(UUID id, String authority, String code, UUID locationId) {

	static LocationSymbolDTO from(LocationSymbol ls) {
		return new LocationSymbolDTO(ls.getId(),
			                     ls.getAuthority(),
			                     ls.getCode(),
			                     ls.getLocation().getId());
	}
}
