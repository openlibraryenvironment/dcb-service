package org.olf.dcb.core.api.types;

import java.util.UUID;

import lombok.Builder;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;
import org.olf.dcb.core.model.DataAgency;

@Builder
@Serdeable
public record AgencyDTO(
	UUID id, 
	@Nullable String code,
	@Nullable String name, 
	@Nullable String hostLMSCode,
	@Nullable String authProfile, 
	@Nullable String idpUrl,
	@Nullable Double longitude,
	@Nullable Double latitude
	) {

	public static AgencyDTO mapToAgencyDTO(DataAgency agency) {
		return AgencyDTO.builder()
			.id(agency.getId())
			.code(agency.getCode())
			.name(agency.getName())
			.authProfile(agency.getAuthProfile())
			.idpUrl(agency.getIdpUrl())
			.hostLMSCode(agency.getHostLms().getCode())
			.longitude(agency.getLongitude())
			.latitude(agency.getLatitude())
			.build();
	}

}
