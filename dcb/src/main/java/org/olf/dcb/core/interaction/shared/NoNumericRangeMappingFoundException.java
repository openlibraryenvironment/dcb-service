package org.olf.dcb.core.interaction.shared;

import org.olf.dcb.core.error.DcbError;

public class NoNumericRangeMappingFoundException extends DcbError {
	public NoNumericRangeMappingFoundException(String message) {
		super(message);
	}
}
