package org.olf.reshare.dcb.core.api.datavalidation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class CitationCommand {
	@NotBlank
	@NotNull
	String bibClusterId;

	public CitationCommand() { }

	public CitationCommand(String bibClusterId) {
		this.bibClusterId = bibClusterId;
	}


	public String getBibClusterId() {
		return bibClusterId;
	}

	public void setBibClusterId(String bibClusterId) {
		this.bibClusterId = bibClusterId;
	}

}
