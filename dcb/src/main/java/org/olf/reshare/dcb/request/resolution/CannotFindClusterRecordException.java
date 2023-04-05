package org.olf.reshare.dcb.request.resolution;

import java.util.UUID;

public class CannotFindClusterRecordException extends RuntimeException {
	public CannotFindClusterRecordException(UUID clusterRecordId) {
		super("Cannot find cluster record for: " + clusterRecordId);
	}
}
