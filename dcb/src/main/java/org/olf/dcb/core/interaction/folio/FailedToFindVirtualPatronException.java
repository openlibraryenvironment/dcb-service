package org.olf.dcb.core.interaction.folio;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;

public class FailedToFindVirtualPatronException extends RuntimeException {
	public FailedToFindVirtualPatronException(@NotNull @NonNull UUID patronId) {
		super("Cannot find virtual patron because requesting patron: \"" + patronId + "\" has no barcode");
	}
}
