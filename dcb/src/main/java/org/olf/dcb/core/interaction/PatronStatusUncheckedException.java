package org.olf.dcb.core.interaction;

/**
 * The patron's own library system could not say whether the patron may borrow, so DCB does not
 * assume they may. The cause carries the system's answer; the message names no barcode, because
 * some systems echo the barcode in their error text.
 */
public class PatronStatusUncheckedException extends RuntimeException {
	public PatronStatusUncheckedException(String localId, String hostLmsCode, Throwable cause) {
		super("Could not check whether patron \"" + localId + "\" may borrow in \"" + hostLmsCode + "\"", cause);
	}
}
