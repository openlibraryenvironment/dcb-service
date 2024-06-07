package org.olf.dcb.request.resolution;

import java.util.UUID;

import lombok.Getter;

@Getter
public class NoBibsForClusterRecordException extends RuntimeException {
	private final UUID clusterRecordId;

	public NoBibsForClusterRecordException(UUID clusterRecordId) {
		super("Cluster record: \"" + clusterRecordId + "\" has no bibs");

		this.clusterRecordId = clusterRecordId;
	}
}
