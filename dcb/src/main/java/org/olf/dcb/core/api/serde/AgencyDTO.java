package org.olf.dcb.core.api.serde;

import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class AgencyDTO {
	

	private UUID id;
	private @Nullable String code;
	private @Nullable String name; 
	private @Nullable String hostLMSCode;
	private @Nullable String authProfile; 
	private @Nullable String idpUrl;
	private @Nullable Double longitude;
	private @Nullable Double latitude;

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
