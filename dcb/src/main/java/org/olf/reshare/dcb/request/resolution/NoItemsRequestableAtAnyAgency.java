package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

/**
 * Specific error for when no items could be chosen during patron request resolution
 */
public class NoItemsRequestableAtAnyAgency extends UnableToResolvePatronRequest {
	public NoItemsRequestableAtAnyAgency(UUID clusterRecordId) {
		super("No requestable items could be found for cluster record: " + clusterRecordId);
	}
}
