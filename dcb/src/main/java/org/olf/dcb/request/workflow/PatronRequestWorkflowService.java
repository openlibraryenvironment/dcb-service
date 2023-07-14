package org.olf.dcb.request.workflow;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
@ExecuteOn(value = TaskExecutors.IO)
public class PatronRequestWorkflowService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestWorkflowService.class);
	
	/**
	 * Duration of delay before task is started Uses ISO-8601 format, as described
	 * <a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-">here</a>
	 */
	@Value("${dcb.request-workflow.state-transition-delay:PT0.0S}")
	private Duration stateTransitionDelay;

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	

	private final List<PatronRequestStateTransition> allTransitions;

	public PatronRequestWorkflowService(
			List<PatronRequestStateTransition> allTransitions,
			PatronRequestRepository patronRequestRepository, PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestAuditService = patronRequestAuditService;
		// By loading the list of all transitions, we can declare new transitions
		// without having to modify the
		// workflow engine constructor every time.
		this.allTransitions = allTransitions;
		
		this.patronRequestRepository = patronRequestRepository;

		log.debug("Initialising workflow engine with available transitions");
		for (PatronRequestStateTransition t : allTransitions) {
			log.debug(t.getClass().getName()); //);
		}
	}

	public void initiate(PatronRequest patronRequest) {
		log.debug("initiate({})", patronRequest);
//		Status status = patronRequest.getStatusCode();
//		String guardCondition = "state==" + status;
//		log.debug("Searching for action when {}", guardCondition);		
		
		progressAll(patronRequest).subscribe();
	}
	
	public Flux<PatronRequest> progressAll(PatronRequest patronRequest) {
		log.debug("progressAll({})", patronRequest);
//		Status status = patronRequest.getStatusCode();
//		String guardCondition = "state==" + status;
//		log.debug("Searching for action when {}", guardCondition);		
		
		return progressUsing(patronRequest, getApplicableTransitionFor(patronRequest));
	}

	public Flux<PatronRequest> progressUsing(PatronRequest patronRequest, Optional<PatronRequestStateTransition> action) {

		if (action.isEmpty()) {
			log.debug("Unable to progress {} - no transformations available from state {}", patronRequest.getId(),
					patronRequest.getStatus());
		}
		
		return Mono.justOrEmpty(action)
			.flatMapMany(transition -> {
				log.debug("found action {} applying transition", action.getClass().getName());
				Flux<PatronRequest> pr = applyTransition(transition, patronRequest);
				log.debug("start applying actions, there may be subsequent transitions possible");
				
				// Resolve as incomplete.
				return pr;
			})
			.transform(getErrorTransformerFor(patronRequest));
		
//		boolean complete = false;
//		if (action != null) {
//			
//		} else {
//			// There ar eno more actions that we can take for this patron request
//			complete = true;
//			log.debug("Unable to progress {} any further - no transformations available from state {}", patronRequest.getId(),
//					patronRequest.getStatusCode());
//		}
//		return complete;
	}

	/**
	 * Hide the details of matching an object against the workflow here...
	 * Eventually we wil likely use JXEL, janino or some other expresion language
	 * here, but for now simple string comparisons are more than sufficient.
	 */
	private Optional<PatronRequestStateTransition> getApplicableTransitionFor(PatronRequest patronRequest) {

		log.debug("getApplicableTransitionFor({})", patronRequest);
		
		return allTransitions.stream()
			.filter(transition -> transition.isApplicableFor(patronRequest))
			.findFirst();
		

//		PatronRequestStateTransition result = null;
//		Iterator<PatronRequestStateTransition> i = allTransitions.iterator();
//		while ((result == null) && (i.hasNext())) {
//			PatronRequestStateTransition candidate = i.next();
//			// log.debug("testing({},{})",candidate.getGuardCondition(),guardCondition);
//			if (candidate.getGuardCondition().equals(guardCondition))
//				result = candidate;
//		}
//		return result;
	}

	private Flux<PatronRequest> applyTransition(PatronRequestStateTransition action, PatronRequest patronRequest) {
		log.debug("applyTransition({},{})", action.getClass().getName(), patronRequest);
		
		return action.attempt(patronRequest)
			.flux()
			.flatMap(patronRequestRepository::saveOrUpdate)
			.concatMap( request -> {
				
				// Recall if there are more...
				return Mono.justOrEmpty(getApplicableTransitionFor( request ))
					.delayElement(stateTransitionDelay)
					.flatMapMany( transition -> applyTransition(transition, request));
				
//				PatronRequestStateTransition next_action = getApplicableTransitionFor(request);
//				if (next_action != null)
//					progress(request, next_action);
			});
		
			
		
				// If the transition was successful, see if there is a next step we can try to
				// take, and if so, try to progress it, otherwise exit
//				.doOnSuccess(result -> {
//					PatronRequestStateTransition next_action = getTransitionFor("state==" + result.getStatusCode());
//					if (next_action != null)
//						progress(result, next_action);
//				}).flatMap(result -> Mono.delay(stateTransitionDelay, Schedulers.boundedElastic()).thenReturn(result))
//				.subscribeOn(Schedulers.boundedElastic()).subscribe();
	}
	
	public Function<Publisher<PatronRequest>,Flux<PatronRequest>> getErrorTransformerFor( PatronRequest patronRequest ) {
		
		final Status fromState = patronRequest.getStatus();
		
		return ( Publisher<PatronRequest> pub  ) -> Flux.from(pub)
				.onErrorResume( throwable -> {
					
					final UUID prId = patronRequest.getId();
					if (prId == null) return Mono.error(throwable);
					
					// When we encounter an error we should set the status in the DB only to avoid,
					// partial state saves.
					
					return Mono.from(patronRequestRepository.updateStatus(prId, Status.ERROR))	
						.flatMap(_void -> patronRequestAuditService.addErrorAuditEntry(patronRequest, fromState, throwable))
						.onErrorResume(saveError -> {
							log.error("Could not update PatronRequest with error state", saveError);
							return Mono.empty();
						})
						.then(Mono.error(throwable));
				});
	}
}
