package org.olf.dcb.request.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
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

import org.olf.dcb.tracking.TrackingHelpers;

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
	
	/**
	 * Try to progress the identified patron request. This is the main entry point for trying to progress a patron request.
	 */
	public Flux<PatronRequest> progressAll(PatronRequest patronRequest) {
		return this.progressAll(patronRequest, false);
	}

	public Flux<PatronRequest> progressAll(PatronRequest patronRequest, boolean pollDownstream) {
		log.debug("WORKFLOW progressAll({})", patronRequest);

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap( ctx -> this.optionallyPollDownstreamSystems(ctx, pollDownstream) )
			.flatMapMany( ctx -> this.progressUsing(ctx, getApplicableTransitionFor(ctx) ));
	}

	/**
	 * If requested to, workflow can poll downstream systems to make sure we have the latest status information
	 * before attempting to progress a request.
	 */
	public Mono<RequestWorkflowContext> optionallyPollDownstreamSystems(RequestWorkflowContext ctx, boolean pollDownstream) {
		if ( pollDownstream ) {
		log.debug("perform downstream polling from workflow service");
			return Mono.just(ctx);
		}

		log.debug("skip downstream polling from workflow service");
		return Mono.just(ctx);
	}

	public Flux<PatronRequest> progressUsing(PatronRequest patronRequest, PatronRequestStateTransition action) {
		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMapMany(ctx -> this.progressUsing(ctx, Optional.ofNullable(action)));
	}
	
	public Flux<PatronRequest> progressUsing(RequestWorkflowContext ctx, Optional<PatronRequestStateTransition> action) {

		if (action.isEmpty()) {
			log.debug("WORKFLOW Unable to progress {} - no transformations available from state {}",
				ctx.getPatronRequest().getId(), ctx.getPatronRequest().getStatus());

			return scheduleNextCheck(ctx)
				.flatMapMany(ctx2 -> {
					return Flux.empty();
				});
		}
		
		return Mono.justOrEmpty(action)
			.flatMapMany(transition -> {
				log.debug("WORKFLOW found action {} applying transition", action.get().getClass().getName());

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

	private Flux<PatronRequest> applyTransition(PatronRequestStateTransition action, RequestWorkflowContext ctx) {

		log.debug("WORKFLOW applyTransition({}, {})", action.getName(), ctx.getPatronRequest().getId());

		// return patronRequestAuditService.addAuditEntry(ctx.getPatronRequest(), ctx.getPatronRequestStateOnEntry(), 
					// ctx.getPatronRequestStateOnEntry(), Optional.of("guard passed : " + action.getName()))

			Map<String,Object> auditData=new HashMap<String,Object>();
			auditData.put("workflowMessages", ctx.getWorkflowMessages());

			return action.attempt(ctx)
			.flux()
			.concatMap(nc -> patronRequestAuditService.addAuditEntry(
				ctx.getPatronRequest(), 
				ctx.getPatronRequestStateOnEntry(), 
				ctx.getPatronRequest().getStatus(), 
				Optional.of("Action completed : " + action.getName()),
				Optional.of(auditData)))
			.concatMap(nc -> patronRequestRepository.saveOrUpdate(nc.getPatronRequest()))
			.concatMap(request -> {
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
					.then(patronRequestAuditService.addErrorAuditEntry(patronRequest,
						fromState, throwable, auditData))
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
