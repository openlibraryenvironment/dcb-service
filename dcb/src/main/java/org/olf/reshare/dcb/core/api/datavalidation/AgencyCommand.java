package org.olf.reshare.dcb.core.api.datavalidation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class AgencyCommand {

	@NotBlank
	@NotNull
	String code;

	public AgencyCommand() { }

	public AgencyCommand(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}
}
