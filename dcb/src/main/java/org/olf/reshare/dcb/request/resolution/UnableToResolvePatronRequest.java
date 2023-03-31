package org.olf.reshare.dcb.request.resolution;

public class UnableToResolvePatronRequest extends RuntimeException {
	public UnableToResolvePatronRequest(String message) { super(message); }
}
