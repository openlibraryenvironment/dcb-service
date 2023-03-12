package org.olf.reshare.dcb.core.api.types;

import java.util.UUID;
import org.olf.reshare.dcb.core.model.DataAgency;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AgencyDTO(UUID id, String code, String name, String hostLMSCode) {

	static AgencyDTO from(DataAgency agency) {
		return new AgencyDTO(agency.getId(),
			             agency.getCode(),
			             agency.getName(),
			             agency.getHostLms().getCode());
	}
}
