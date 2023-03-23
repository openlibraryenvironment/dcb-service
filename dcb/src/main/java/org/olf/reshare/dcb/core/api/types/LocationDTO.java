package org.olf.reshare.dcb.core.api.types;

import java.util.UUID;
import org.olf.reshare.dcb.core.model.Location;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LocationDTO(UUID id, String code, String name, String type, UUID agency, Boolean isPickup) {


	static LocationDTO from(Location location) {
		return new LocationDTO(
                                  location.getId(),
			          location.getCode(),
			          location.getName(),
                                  location.getType(),
                                  location.getAgency(),
                                  location.getIsPickup());
	}
}
