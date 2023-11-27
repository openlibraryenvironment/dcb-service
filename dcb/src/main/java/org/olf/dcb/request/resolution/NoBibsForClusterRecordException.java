package org.olf.dcb.request.resolution;

import java.util.UUID;

public class NoBibsForClusterRecordException extends RuntimeException {
	public NoBibsForClusterRecordException(UUID clusterRecordId) {
		super("Cluster record: \"" + clusterRecordId + "\" has no bibs");
	}
}
