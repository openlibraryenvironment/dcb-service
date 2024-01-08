package org.olf.dcb.core.interaction.folio;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;

public class FailedToFindVirtualPatronException extends RuntimeException {
	private FailedToFindVirtualPatronException(String message) {
		super(message);
	}

	public static FailedToFindVirtualPatronException noBarcode(@NotNull @NonNull UUID patronId) {
		return new FailedToFindVirtualPatronException(
			"Cannot find virtual patron because requesting patron: \"" + patronId + "\" has no barcode");
	}

	public static FailedToFindVirtualPatronException notFound(
		@NotNull @NonNull String localPatronId, String hostLmsCode) {

		return new FailedToFindVirtualPatronException(
			"Cannot find virtual patron because no local record was found with ID: \"" + localPatronId
				+ "\" in Host LMS: \"" + hostLmsCode + "\"");
	}
}
