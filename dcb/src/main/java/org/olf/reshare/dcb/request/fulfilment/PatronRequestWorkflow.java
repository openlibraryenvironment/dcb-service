package org.olf.reshare.dcb.request.fulfilment;

import java.time.Duration;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.utils.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class PatronRequestWorkflow {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflow.class);

	private final PatronRequestResolutionStateTransition patronRequestResolutionStateTransition;
	private final BackgroundExecutor backgroundExecutor;
	private final Duration stateTransitionDelay;

	/**
	 *
	 * @param patronRequestResolutionStateTransition the resolution state transition
	 * @param backgroundExecutor the background executor executing the next transition
	 * @param stateTransitionDelay Duration of delay before task is started
	 * Uses ISO-8601 format, as described <a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-">here</a>
	 */
	public PatronRequestWorkflow(
		PatronRequestResolutionStateTransition patronRequestResolutionStateTransition,
		BackgroundExecutor backgroundExecutor,
		@Value("${dcb.request-workflow.state-transition-delay:PT0.0S}") Duration stateTransitionDelay) {

		this.patronRequestResolutionStateTransition = patronRequestResolutionStateTransition;
		this.backgroundExecutor = backgroundExecutor;
		this.stateTransitionDelay = stateTransitionDelay;
	}


	public void initiate(PatronRequest patronRequest) {
		log.debug("initializeRequestWorkflow({})", patronRequest);

		backgroundExecutor.executeAsynchronously(
			patronRequestResolutionStateTransition.attempt(patronRequest),
			stateTransitionDelay);
	}
}
