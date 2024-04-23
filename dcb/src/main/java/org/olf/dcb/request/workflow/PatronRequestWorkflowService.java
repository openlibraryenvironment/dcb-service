package org.olf.dcb.request.workflow;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingHelpers;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

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

		this.progressAll(patronRequest)
			.doFinally(signalType -> log.info("WORKFLOW Completed processing for {}", patronRequest.getId()))
			.subscribe();
	}

	/**
	 * Try to progress the identified patron request. This is the main entry point for trying to progress a patron request.
	 */
	public Mono<PatronRequest> progressAll(PatronRequest patronRequest) {
		log.debug("WORKFLOW progressAll({})", patronRequest);

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap( ctx -> this.progressUsing(ctx, getApplicableTransitionFor(ctx) ));
	}

	/**
	 * For when we already have a request workflow context in our hand
	 * @param ctx context to use when progressing workflow
	 * @return provided context
	 */
	public Mono<RequestWorkflowContext> progressUsing(RequestWorkflowContext ctx) {
		return Mono.just(ctx)
			.flatMap( ctx2 -> this.progressUsing(ctx2, getApplicableTransitionFor(ctx2) ))
			.thenReturn(ctx);
	}

	public Mono<PatronRequest> progressUsing(PatronRequest patronRequest,
		PatronRequestStateTransition action) {

		return requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> this.progressUsing(ctx, Optional.ofNullable(action)));
	}
	
	public Mono<PatronRequest> progressUsing(RequestWorkflowContext ctx,
		Optional<PatronRequestStateTransition> action) {

		// We don't schedule the next check until we have exhausted all our possible expansions/progressions
		// apply Transition will call recursively until we run out of possible actions, then we pass through this leg
		// and set the next check.
		if (action.isEmpty()) {
			log.debug("WORKFLOW Unable to progress {} - no transformations available from state {}",
				ctx.getPatronRequest().getId(), ctx.getPatronRequest().getStatus());

			return incrementPollingForThisState(ctx)
				// note: the polling increment will only be saved by scheduleNextCheck
				.flatMap(this::scheduleNextCheck)
				.flatMap(ctx2 -> Mono.empty());
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

	public Stream<PatronRequestStateTransition> getPossibleStateTransitionsFor(
		RequestWorkflowContext ctx) {

		final var sortedTransitions = allTransitions.stream()
			.sorted(Comparator.comparing(PatronRequestStateTransition::getName).reversed())
			.toList();

		return sortedTransitions.stream()
			.filter(transition -> (transition.isApplicableFor(ctx) && transition.attemptAutomatically()));
	}

	private Mono<PatronRequest> applyTransition(PatronRequestStateTransition action,
		RequestWorkflowContext ctx) {

		log.debug("WORKFLOW applyTransition({}, {})", action.getName(), ctx.getPatronRequest().getId());

		final var auditData = new HashMap<String, Object>();

		auditData.put("workflowMessages", ctx.getWorkflowMessages());

		return action.attempt(ctx)
		.flatMap(nc -> patronRequestAuditService.addAuditEntry(
			ctx.getPatronRequest(),
			ctx.getPatronRequestStateOnEntry(),
			ctx.getPatronRequest().getStatus(),
			Optional.of("Action completed : " + action.getName()),
			Optional.of(auditData)))
		// reset the poll count for state change
		.map(getPatronRequestWithPollCount())
		.flatMap(pr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
		.flatMap(request -> {
			// Recursively call progress all in case there are subsequent steps we can apply
			return this.progressAll(ctx.getPatronRequest());
		});
	}

	private static Function<PatronRequestAudit, PatronRequest> getPatronRequestWithPollCount() {
		return audit -> {
			if (audit.getFromStatus() != audit.getToStatus()) {
				log.info("resetting poll count for status {}", audit.getToStatus());
				return audit.getPatronRequest().setPollCountForCurrentStatus(0);
			}
			return audit.getPatronRequest();
		};
	}

	public Function<Publisher<PatronRequest>, Flux<PatronRequest>> getErrorTransformerFor(
		PatronRequest patronRequest) {
		
		final var fromState = patronRequest.getStatus();

		return pub -> Flux.from(pub)
			.onErrorResume(throwable -> Mono.defer(() -> {
				// If we don't do this, then a subsequent save of the patron request can overwrite the status we explicitly set
				patronRequest.setStatus(ERROR);

				final var prId = patronRequest.getId();

				if (prId == null) {
					return Mono.error(throwable);
				}

				final var message = determineMessage(throwable);

				final var auditData = new HashMap<String, Object>();

				if (throwable instanceof Problem problem) {
					if (isNotEmpty(problem.getDetail())) {
						auditData.put("detail", problem.getDetail());
					}

					auditData.putAll(problem.getParameters());
				}

				// When we encounter an error we should set the status in the DB only to avoid,
				// partial state saves.
				log.error("WORKFLOW update patron request {} to error state ({}) - {}",
					prId, message, throwable.getClass().getName());

				return Mono.from(patronRequestRepository.updateStatusWithError(prId, message))
					.then(patronRequestAuditService.addErrorAuditEntry(
						patronRequest, fromState, message, auditData))
					.onErrorResume(saveError -> {
						log.error("WORKFLOW Could not update PatronRequest with error state", saveError);
						return Mono.empty();
					})
					.then(Mono.error(throwable));
			}));
	}

	private static String determineMessage(Throwable throwable) {
		if (throwable instanceof ThrowableProblem problem) {
			return isNotEmpty(problem.getTitle())
				? problem.getTitle()
				: problem.getMessage();
		}

		return throwable.getMessage();
	}

	private Optional<PatronRequestStateTransition> getApplicableTransitionFor(
		RequestWorkflowContext ctx) {

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
		final var patronRequest = ctx.getPatronRequest();

		final var duration = TrackingHelpers.getDurationFor(patronRequest.getStatus());

		Instant next_poll = null;

		if (duration.isEmpty()) {
			log.debug("No scheduled check due");
		}
		else {
			next_poll = Instant.now().plus(duration.get());
			log.debug("scheduleNextCheck Extracted duration {} next check is {}",duration,next_poll);
		}
		return Mono.from(patronRequestRepository.updateNextScheduledPollAndPollCountForCurrentStatus(
			ctx.getPatronRequest().getId(), next_poll, patronRequest.getPollCountForCurrentStatus()))
			.thenReturn(ctx);
	}

	private Mono<RequestWorkflowContext> incrementPollingForThisState(RequestWorkflowContext ctx) {
		var patronRequest = ctx.getPatronRequest();
		final Integer currentPollCount = patronRequest.getPollCountForCurrentStatus();
		final Integer incrementedPoll = currentPollCount == null || currentPollCount == 0 ? 1 : currentPollCount + 1;
		log.debug("currentPollCount {} is being incremented to {}: ", currentPollCount, incrementedPoll);

		patronRequest = patronRequest.setPollCountForCurrentStatus(incrementedPoll);
		return Mono.just(ctx.setPatronRequest(patronRequest));
	}
}
