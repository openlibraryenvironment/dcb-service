package org.olf.dcb.core.interaction;

/**
 * A library system's record is not a virtual patron DCB made for this borrower, so DCB leaves it
 * alone. The message names the system and its record id, never a barcode.
 */
public class NotDcbsVirtualPatronException extends RuntimeException {
	public NotDcbsVirtualPatronException(String message) {
		super(message);
	}
}
