package org.olf.dcb.core.interaction;

public class PatronNotFoundInHostLmsException extends RuntimeException {
	public PatronNotFoundInHostLmsException(String localId, String hostLmsCode) {
		super("Patron \"" + localId + "\" is not recognised in \"" + hostLmsCode + "\"");
	}
}
