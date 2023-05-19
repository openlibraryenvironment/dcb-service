package org.olf.reshare.dcb.request.fulfilment;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.time.Duration;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Prototype
public class PatronRequestWorkflow {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflow.class);

	private final PatronRequestResolutionStateTransition patronRequestResolutionStateTransition;
	private final PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition;
	private final Duration stateTransitionDelay;

	/**
	 * @param patronRequestResolutionStateTransition             the resolution state transition
	 * @param placePatronRequestAtSupplyingAgencyStateTransition the place at supplying agency state transition
	 * @param backgroundExecutor                                 the background executor executing the next transition
	 * @param stateTransitionDelay                               Duration of delay before task is started
	 * Uses ISO-8601 format, as described <a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-">here</a>
	 */
	public PatronRequestWorkflow(
		PatronRequestResolutionStateTransition patronRequestResolutionStateTransition,
		PlacePatronRequestAtSupplyingAgencyStateTransition placePatronRequestAtSupplyingAgencyStateTransition,
		@Value("${dcb.request-workflow.state-transition-delay:PT0.0S}") Duration stateTransitionDelay) {

		this.patronRequestResolutionStateTransition = patronRequestResolutionStateTransition;
		this.placePatronRequestAtSupplyingAgencyStateTransition = placePatronRequestAtSupplyingAgencyStateTransition;
		this.stateTransitionDelay = stateTransitionDelay;
	}

	public void initiate(PatronRequest patronRequest) {
		log.debug("initializeRequestWorkflow({})", patronRequest);

		String status = patronRequest.getStatusCode();
		switch (status) {
			case SUBMITTED_TO_DCB -> resolvePatronRequestTransition(patronRequest);
			case RESOLVED -> placePatronRequestAtSupplyingAgencyTransition(patronRequest);
			default -> log.debug("Cannot make transition with status: " + status);
		};
	}

	private void placePatronRequestAtSupplyingAgencyTransition(PatronRequest patronRequest) {
		log.debug("placePatronRequestAtSupplyingAgencyTransition({})", patronRequest);
		placePatronRequestAtSupplyingAgencyStateTransition.attempt(patronRequest)
			.flatMap(result -> Mono.delay(stateTransitionDelay, Schedulers.boundedElastic())
				.thenReturn(result))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe();
	}

	private void resolvePatronRequestTransition(PatronRequest patronRequest) {
		log.debug("resolvePatronRequestTransition({})", patronRequest);
		patronRequestResolutionStateTransition.attempt(patronRequest)
			.doOnSuccess(this::initiate)
			.flatMap(result -> Mono.delay(stateTransitionDelay, Schedulers.boundedElastic())
				.thenReturn(result))
			.subscribeOn(Schedulers.boundedElastic())
			.subscribe();
	}
}
