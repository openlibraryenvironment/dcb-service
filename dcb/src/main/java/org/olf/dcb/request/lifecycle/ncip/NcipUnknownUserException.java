package org.olf.dcb.request.lifecycle.ncip;

/** A LookupUserResponse whose Problem is the NCIP "Unknown User" type: the identifier matched no patron. */
public class NcipUnknownUserException extends NcipProblemException {
	public NcipUnknownUserException(String message) {
		super(message);
	}
}
