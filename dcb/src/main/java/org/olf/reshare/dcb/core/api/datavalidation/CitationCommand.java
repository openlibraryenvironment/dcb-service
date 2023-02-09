package org.olf.reshare.dcb.core.api.datavalidation;

import io.micronaut.core.annotation.Introspected;
import org.immutables.value.Value;
import services.k_int.interaction.DefaultImmutableStyle;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;
import java.util.function.Consumer;

@Introspected
public class CitationCommand {
	@NotBlank
	@NotNull
	String bibClusterId;

	public String getBibClusterId() {
		return bibClusterId;
	}

	public void setBibClusterId(String bibClusterId) {
		this.bibClusterId = bibClusterId;
	}

}
