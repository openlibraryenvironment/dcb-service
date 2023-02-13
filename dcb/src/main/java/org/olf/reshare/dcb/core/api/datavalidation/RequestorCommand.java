package org.olf.reshare.dcb.core.api.datavalidation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class RequestorCommand {

	@NotBlank
	@NotNull
	String identifiier;

	AgencyCommand agency;

	public RequestorCommand() { }

	public RequestorCommand(String identifiier, AgencyCommand agency) {
		this.identifiier = identifiier;
		this.agency = agency;
	}

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
