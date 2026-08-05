package org.olf.dcb.core.interaction;

/**
 * Selects the timestamp used to start the next OAI-PMH harvest after paging completes.
 */
public enum OaiPmhResumptionPolicy {
	/** Resume inclusively from the greatest record datestamp actually observed. */
	HIGHEST_TIMESTAMP,
	/** Resume from DCB's clock at the start of the completed fetch. */
	INTERNAL_CLOCK
}
