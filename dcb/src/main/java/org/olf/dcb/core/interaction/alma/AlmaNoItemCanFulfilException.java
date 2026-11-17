package org.olf.dcb.core.interaction.alma;

import org.olf.dcb.core.interaction.RequestOptionsReport;

/** Alma's 401129, with what Alma said the patron may request instead. */
public class AlmaNoItemCanFulfilException extends AlmaHostLmsClientException {
	private final transient RequestOptionsReport options;

	AlmaNoItemCanFulfilException(RequestOptionsReport options, String message, Throwable cause) {
		super(message, cause);
		this.options = options;
	}

	public RequestOptionsReport getOptions() {
		return options;
	}
}
