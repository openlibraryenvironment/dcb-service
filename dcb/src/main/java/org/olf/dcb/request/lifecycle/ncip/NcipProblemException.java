package org.olf.dcb.request.lifecycle.ncip;

public class NcipProblemException extends RuntimeException {
	public NcipProblemException(String message) {
		super(message);
	}

	public NcipProblemException(String message, Throwable cause) {
		super(message, cause);
	}
}
