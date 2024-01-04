package org.olf.dcb.core.model;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;

public class NoHomeBarcodeException extends RuntimeException {
	public NoHomeBarcodeException(@NotNull @NonNull UUID patronId) {
		super("Patron \"" + patronId + "\" has no home barcode");
	}
}
