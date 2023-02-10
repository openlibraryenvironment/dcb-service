package org.olf.reshare.dcb.core.api.datavalidation;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Serdeable
public class RequestorCommand {

	@NotBlank
	@NotNull
	String identifiier;

	AgencyCommand agency;

	public String getIdentifiier() {
		return identifiier;
	}

	public void setIdentifiier(String identifiier) {
		this.identifiier = identifiier;
	}

	public AgencyCommand getAgency() {
		return agency;
	}

	public void setAgency(AgencyCommand agency) {
		this.agency = agency;
	}
}
