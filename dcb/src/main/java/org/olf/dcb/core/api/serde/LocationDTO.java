package org.olf.dcb.core.api.serde;

import java.util.UUID;

import org.olf.dcb.core.model.Location;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LocationDTO(
	UUID id, 
	String code, 
	String name, 
	String type, 
	UUID agency, 
	Boolean isPickup, 
	Double longitude, 
	Double latitude
) {
	static LocationDTO from(Location location) {

                UUID agency = ( location.getAgency() != null ) ? location.getAgency().getId() : null;

		return new LocationDTO(
                                  location.getId(),
			          location.getCode(),
			          location.getName(),
                                  location.getType(),
                                  agency,
                                  location.getIsPickup(),
                                  location.getLongitude(),
                                  location.getLatitude()
		);
	}
}
