package org.olf.reshare.dcb.request.fulfilment;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.utils.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

@Singleton
public class PatronRequestWorkflow {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflow.class);

	private final PatronRequestResolutionStateTransition patronRequestResolutionStateTransition;
	private final BackgroundExecutor backgroundExecutor;

	public PatronRequestWorkflow(
		PatronRequestResolutionStateTransition patronRequestResolutionStateTransition,
		BackgroundExecutor backgroundExecutor) {

		this.patronRequestResolutionStateTransition = patronRequestResolutionStateTransition;
		this.backgroundExecutor = backgroundExecutor;
	}

	public void initiate(PatronRequest patronRequest) {
		log.debug("initializeRequestWorkflow({})", patronRequest);

		backgroundExecutor.executeAsynchronously(
			patronRequestResolutionStateTransition.attempt(patronRequest));
	}
}
