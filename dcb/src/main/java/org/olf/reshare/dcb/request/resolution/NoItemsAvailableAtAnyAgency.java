package org.olf.reshare.dcb.request.resolution;

/**
 * Specific error for when no items found during patron request resolution
 */
public class NoItemsAvailableAtAnyAgency extends UnableToResolvePatronRequest {
	public NoItemsAvailableAtAnyAgency(String message) {
		super(message);
	}
}
