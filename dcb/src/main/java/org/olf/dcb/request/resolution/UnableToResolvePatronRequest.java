package org.olf.dcb.request.resolution;

public class UnableToResolvePatronRequest extends RuntimeException {
	public UnableToResolvePatronRequest(String message) { super(message); }
}
