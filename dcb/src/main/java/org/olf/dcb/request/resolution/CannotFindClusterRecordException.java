package org.olf.dcb.request.resolution;

import java.util.UUID;

import lombok.Getter;

@Getter
public class CannotFindClusterRecordException extends RuntimeException {
	private final UUID clusterRecordId;

	public CannotFindClusterRecordException(UUID clusterRecordId) {
		super("Cannot find cluster record for: " + clusterRecordId);

		this.clusterRecordId = clusterRecordId;
	}
}
