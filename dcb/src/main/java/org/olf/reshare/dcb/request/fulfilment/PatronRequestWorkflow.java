package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Iterator;

import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

@Prototype
public class PatronRequestWorkflow {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflow.class);

	private final Duration stateTransitionDelay;

        private final List<PatronRequestStateTransition> allTransitions;

	/**
	 * @param stateTransitionDelay                               Duration of delay before task is started
	 * Uses ISO-8601 format, as described <a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-">here</a>
	 */
	public PatronRequestWorkflow(List<PatronRequestStateTransition> allTransitions,	@Value("${dcb.request-workflow.state-transition-delay:PT0.0S}") Duration stateTransitionDelay) {

		this.stateTransitionDelay = stateTransitionDelay;

		// By loading the list of all transitions, we can declare new transitions without having to modify the
		// workflow engine constructor every time.
		this.allTransitions = allTransitions;

		log.debug("Initialising workflow engine with available transitions");
		for ( PatronRequestStateTransition t : allTransitions ) {
			log.debug("{} attempt when {}",t.getClass().getName(),t.getGuardCondition());
		}
	}

	public boolean initiate(PatronRequest patronRequest) {
                log.debug("initiate({})", patronRequest);
                String status = patronRequest.getStatusCode();
                String guardCondition="state=="+status;
                log.debug("Searching for action when {}",guardCondition);
                PatronRequestStateTransition action = getTransitionFor(guardCondition);
		return progress(patronRequest, action);
        }

	public boolean progress(PatronRequest patronRequest, PatronRequestStateTransition action) {

		boolean complete = false;
		if ( action != null ) {
			log.debug("found action {} applying transition",action.getClass().getName());
			applyTransition(action, patronRequest);
			log.debug("action applied, there may be subsequent transitions possible");
		}
		else {
			// There ar eno more actions that we can take for this patron request
			complete = true;
			log.debug("Unable to progress {} any further - no transformations available from state {}",patronRequest.getId(),patronRequest.getStatusCode());
		} 
		return complete;
	}

	/**
	 * Hide the details of matching an object against the workflow here... Eventually we wil likely use JXEL, janino or some other
	 * expresion language here, but for now simple string comparisons are more than sufficient.
	 */
	private PatronRequestStateTransition getTransitionFor(String guardCondition) {

		log.debug("getTransitionFor({})",guardCondition);

		PatronRequestStateTransition result = null;
		Iterator<PatronRequestStateTransition> i = allTransitions.iterator();
		while ( ( result == null ) && ( i.hasNext() ) ) {
			PatronRequestStateTransition candidate = i.next();
			log.debug("testing({},{})",candidate.getGuardCondition(),guardCondition);
			if ( candidate.getGuardCondition().equals(guardCondition) )
				result = candidate;
		}
		return result;
	}


        private void applyTransition(PatronRequestStateTransition action, PatronRequest patronRequest) {
                log.debug("applyTransition({},{})", action.getClass().getName(), patronRequest);
                action.attempt(patronRequest)
			// If the transition was successful, see if there is a next step we can try to take, and if so, try to progress it, otherwise exit
			.doOnSuccess(result -> { 
						PatronRequestStateTransition next_action = getTransitionFor("state=="+result.getStatusCode());
                                                if (next_action != null ) progress(result,next_action); 
                                           } )
                        .flatMap(result -> Mono.delay(stateTransitionDelay, Schedulers.boundedElastic()).thenReturn(result))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
        }

}
