package org.olf.dcb.core.interaction.shared;

import org.olf.dcb.core.error.DcbError;
public class MissingParameterException extends DcbError {

	public MissingParameterException(String parameterName) {
		super(parameterName + " is missing.");
	}
}
