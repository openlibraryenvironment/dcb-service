package org.olf.dcb.request;

import org.olf.dcb.core.model.clustering.ClusterRecord;

public class CannotFindSelectedBibException extends RuntimeException {
	public CannotFindSelectedBibException(ClusterRecord clusterRecord) {
		super("Unable to locate selected bib " + clusterRecord.getSelectedBib()
			+ " for cluster " + clusterRecord.getId());
	}
}
