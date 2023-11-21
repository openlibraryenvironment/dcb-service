package org.olf.dcb.core.interaction.polaris.exceptions;

public class HoldRequestTypeException extends RuntimeException {
	public HoldRequestTypeException(String holdtype) {
		super("Unexpected hold request type: " + holdtype);
	}
}
