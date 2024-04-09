package org.olf.dcb.request.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingHelpers;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.zalando.problem.DefaultProblem;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@ExecuteOn(value = TaskExecutors.IO)
public class PatronRequestWorkflowService {
	/**
	 * Duration of delay before task is started Uses ISO-8601 format, as described
	 * <a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-">here</a>
	 */
	@Value("${dcb.request-workflow.state-transition-delay:PT0.0S}")
	private Duration stateTransitionDelay;

	private final PatronRequestRepository patronRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;

	private final List<PatronRequestStateTransition> allTransitions;

	private final RequestWorkflowContextHelper requestWorkflowContextHelper;

	public PatronRequestWorkflowService(List<PatronRequestStateTransition> allTransitions,
		PatronRequestRepository patronRequestRepository,
		PatronRequestAuditService patronRequestAuditService,
		RequestWorkflowContextHelper requestWorkflowContextHelper) {

		this.patronRequestAuditService = patronRequestAuditService;
		// By loading the list of all transitions, we can declare new transitions
		// without having to modify the
		// workflow engine constructor every time.
		this.allTransitions = allTransitions;
		this.patronRequestRepository = patronRequestRepository;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		log.debug("Initialising workflow engine with available transitions");
		for (PatronRequestStateTransition t : allTransitions) {
			log.debug(t.getClass().getName());
		}
	}

	/**
	 * Initiate a new request workflow
	 */
	public void initiate(PatronRequest patronRequest) {
		log.info("WORKFLOW initiate({})", patronRequest);

		// Inspired by https://blogs.ashrithgn.com/adding-transaction-trace-id-correlation-id-for-each-request-in-micronaut-for-tracing-the-log-easily/
		MDC.put("prID", patronRequest.getId().toString());

		progressAll(patronRequest)
			.doFinally(signalType -> log.info("WORKFLOW Completed processing for {}", patronRequest.getId()))
			.subscribe();
	}
	
	public Mono<PatronRequest> progressAll(UUID patronRequestId) {
		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap(pr -> this.progressAll(pr, false));
	}

	/**
	 * Try to progress the identified patron request. This is the main entry point for trying to progress a patron request.
	 */
	public Mono<PatronRequest> progressAll(PatronRequest patronRequest) {
		return this.progressAll(patronRequest, false);
	}

	public Mono<PatronRequest> progressAll(PatronRequest patronRequest, boolean pollDownstream) {
		log.debug("WORKFLOW progressAll({})", patronRequest);

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap( ctx -> this.progressUsing(ctx, getApplicableTransitionFor(ctx) ));
	}

	/**
	 * For when we already have a request workflow context in our hand
	 * @param ctx
	 * @return
	 */
	public Mono<RequestWorkflowContext> progressUsing(RequestWorkflowContext ctx) {
		return Mono.just(ctx)
			.flatMap( ctx2 -> this.progressUsing(ctx2, getApplicableTransitionFor(ctx2) ))
			.thenReturn(ctx);
	}

	public Mono<PatronRequest> progressUsing(PatronRequest patronRequest, PatronRequestStateTransition action) {
		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> this.progressUsing(ctx, Optional.ofNullable(action)));
	}
	
	public Mono<PatronRequest> progressUsing(RequestWorkflowContext ctx, Optional<PatronRequestStateTransition> action) {

		// We don't schedule the next check until we have exhausted all our possible expansions/progressions
		// apply Transition will call recursively until we run out of possible actions, then we pass through this leg
		// and set the next check.
		if (action.isEmpty()) {
			log.debug("WORKFLOW Unable to progress {} - no transformations available from state {}",
				ctx.getPatronRequest().getId(), ctx.getPatronRequest().getStatus());

			return scheduleNextCheck(ctx)
				.flatMap(ctx2 -> {
					return Mono.empty();
				});
		}
		
		return Mono.justOrEmpty(action)
			.flatMap(transition -> {
				log.debug("WORKFLOW found action {} applying transition", transition.getClass().getName());

				final var pr = applyTransition(transition, ctx);

				log.debug("WORKFLOW start applying actions, there may be subsequent transitions possible");

				// Resolve as incomplete.
				return pr;
			});
	}

	public Stream<PatronRequestStateTransition> getPossibleStateTransitionsFor(RequestWorkflowContext ctx) {
		final var sortedTransitions = allTransitions.stream()
			.sorted(Comparator.comparing(PatronRequestStateTransition::getName).reversed())
			.toList();

		return sortedTransitions.stream()
			.filter(transition -> (transition.isApplicableFor(ctx) && transition.attemptAutomatically()));
	}

	private Mono<PatronRequest> applyTransition(PatronRequestStateTransition action, RequestWorkflowContext ctx) {

		log.debug("WORKFLOW applyTransition({}, {})", action.getName(), ctx.getPatronRequest().getId());

		// return patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), ctx.getPatronRequestStateOnEntry(), 
					// ctx.getPatronRequestStateOnEntry(), Optional.of("guard passed : " + action.getName()))

			Map<String,Object> auditData=new HashMap<String,Object>();
			auditData.put("workflowMessages", ctx.getWorkflowMessages());

			return action.attempt(ctx)
			.flatMap(nc -> patronRequestAuditService.addAuditEntry(
				ctx.getPatronRequest(), 
				ctx.getPatronRequestStateOnEntry(), 
				ctx.getPatronRequest().getStatus(), 
				Optional.of("Action completed : " + action.getName()),
				Optional.of(auditData)))
			.flatMap(nc -> Mono.from(patronRequestRepository.saveOrUpdate(nc.getPatronRequest())))
			.flatMap(request -> {
				// Recursively call progress all in case there are subsequent steps we can apply
				return progressAll(ctx.getPatronRequest());
			});
	}
	
	public Function<Publisher<PatronRequest>, Flux<PatronRequest>> getErrorTransformerFor(
		PatronRequest patronRequest) {
		
		final Status fromState = patronRequest.getStatus();

		return (Publisher<PatronRequest> pub) -> Flux.from(pub)
			.onErrorResume(throwable -> Mono.defer(() -> {
				// If we don't do this, then a subsequent save of the patron request can overwrite the status we explicitly set
				patronRequest.setStatus(Status.ERROR);

				final var prId = patronRequest.getId();

				if (prId == null) {
					return Mono.error(throwable);
				}

				// When we encounter an error we should set the status in the DB only to avoid,
				// partial state saves.

				log.error("WORKFLOW update patron request {} to error state ({}) - {}",
					prId, throwable.getMessage(), throwable.getClass().getName());

				Map<String, Object> auditData = null;

				if (throwable instanceof DefaultProblem problem) {
					auditData = problem.getParameters();
				}

				return Mono.from(patronRequestRepository.updateStatusWithError(prId, throwable.getMessage()))
					.then(patronRequestAuditService.addErrorAuditEntry(
						patronRequest, fromState, throwable.getMessage(),
						auditData))
					.onErrorResume(saveError -> {
						log.error("WORKFLOW Could not update PatronRequest with error state", saveError);
						return Mono.empty();
					})
					.then(Mono.error(throwable));
			}));
	}

	private Optional<PatronRequestStateTransition> getApplicableTransitionFor(RequestWorkflowContext ctx) {
		log.debug("getApplicableTransitionFor...");

		log.debug("WORKFLOW Possible transitions: {}", getPossibleStateTransitionsFor(ctx)
			.map(PatronRequestStateTransition::getName)
			.toList());

		final var firstApplicable = getPossibleStateTransitionsFor(ctx).findFirst();

		log.debug("WORKFLOW First applicable transition: {}", firstApplicable
			.map(PatronRequestStateTransition::getName)
			.orElse("None"));

		return firstApplicable;
	}

	private Mono<RequestWorkflowContext> scheduleNextCheck(RequestWorkflowContext ctx) {
		Optional<Duration> d = TrackingHelpers.getDurationFor(ctx.getPatronRequest().getStatus());
		Instant next_poll = null;
		if ( d.isEmpty() ) {
			log.debug("No scheduled check due");
		}
		else {
			next_poll = Instant.now().plus(d.get());
			log.debug("scheduleNextCheck Extracted duration {} next check is {}",d,next_poll);
		}
		return Mono.from(patronRequestRepository.updateNextScheduledPoll(ctx.getPatronRequest().getId(), next_poll))
			.thenReturn(ctx);
	}
}
