package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.error.DcbError;

import io.micronaut.core.annotation.NonNull;

public class OaiResumptionTokenError extends DcbError {
	private static final long serialVersionUID = 5549965440298655608L;
	public OaiResumptionTokenError(@NonNull String message) {
		super(message);
	}
}
