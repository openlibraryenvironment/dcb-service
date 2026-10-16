package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.olf.dcb.core.model.EventType.FAILED_CHECK;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.StringUtils.truncate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.UnhandledExceptionProblem;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.storage.EventLogRepository;
import org.zalando.problem.Problem;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PatronRequestPreflightChecksService {
	static final String DURATION_METRIC = "dcb.preflight.duration";
	static final String CHECK_DURATION_METRIC = "dcb.preflight.check.duration";

	private final List<PreflightCheck> checks;
	private final EventLogRepository eventLogRepository;
	private final IntMessageService intMessageService;
	private final Duration timeout;
	private final MeterRegistry meterRegistry;

	public PatronRequestPreflightChecksService(Collection<PreflightCheck> checks,
		EventLogRepository eventLogRepository, IntMessageService intMessageService,
		PreflightChecksTimeout timeout,
		MeterRegistry meterRegistry) {

		this.checks = List.copyOf(checks);
		this.eventLogRepository = eventLogRepository;
		this.intMessageService = intMessageService;
		this.timeout = timeout.value();
		this.meterRegistry = meterRegistry;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		log.info("Perform preflight checks {}", command);

		return performChecks(command)
			// Has to go before flatMap that possibly raises error
			.onErrorMap(UnhandledExceptionProblem::new)
			.doOnError(UnhandledExceptionProblem.class, unhandledExceptionProblem ->
				log.error("Unhandled error in preflight checks, message: {}, additional info: {}",
					getValue(unhandledExceptionProblem, Problem::getDetail, "No Message"),
					getValue(unhandledExceptionProblem, Problem::getParameters, Map.of())))
			.flatMap(results -> {
				if (allPassed(results)) {
					log.info("request passed preflight {}", command);
					return Mono.just(command);
				}

				// It's worth logging the failures as it might be a sign of some fundamental systems issue
				log.warn("request {} failed preflight {}", command, results);

				return reportFailedChecksInEventLog(results, command)
					.flatMap(reportedResults -> Mono.error(
						new PreflightCheckFailedException(failedChecksOnly(reportedResults))));
			});
	}

	// Bounded here, before anything is saved, so a slow member system refuses the request rather than
	// holding the caller open. take, not timeout: what finished in time says which check did not
	private Mono<List<CheckResult>> performChecks(PlacePatronRequestCommand command) {
		return Mono.defer(() -> {
			final long started = System.nanoTime();

			return Flux.fromIterable(checks)
				.doOnNext(check -> log.info("Preflight check: {}", check))
				.concatMap(check -> timed(check, command))
				.take(timeout)
				.collectList()
				.map(finished -> conclude(command, finished, System.nanoTime() - started))
				.doOnError(error -> record("error", System.nanoTime() - started));
		});
	}

	private Mono<TimedCheck> timed(PreflightCheck check, PlacePatronRequestCommand command) {
		// An empty answer is no failures, not an unfinished check
		return check.check(command)
			.defaultIfEmpty(List.of())
			.elapsed()
			.map(answer -> new TimedCheck(nameOf(check), answer.getT1(), answer.getT2()));
	}

	private List<CheckResult> conclude(PlacePatronRequestCommand command, List<TimedCheck> finished,
		long elapsedNanos) {

		final var timedOut = finished.size() < checks.size();

		final List<CheckResult> results = timedOut
			? List.of(CheckResult.failedUm("PREFLIGHT_CHECKS_TIMED_OUT",
				"Preflight checks did not finish within " + timeout,
				intMessageService.getMessage("PREFLIGHT_CHECKS_TIMED_OUT")))
			: finished.stream().flatMap(check -> check.results().stream()).toList();

		final var outcome = timedOut ? "timed_out" : allPassed(results) ? "passed" : "failed";

		record(outcome, elapsedNanos);
		finished.forEach(check -> checkTimer(check.name()).record(check.millis(), MILLISECONDS));

		log.info(summary(command, outcome, NANOSECONDS.toMillis(elapsedNanos), finished,
			timedOut ? nameOf(checks.get(finished.size())) : null, results));

		return results;
	}

	private void record(String outcome, long elapsedNanos) {
		Timer.builder(DURATION_METRIC)
			.description("Time to run every preflight check for one placement")
			.tag("outcome", outcome)
			.publishPercentileHistogram()
			.maximumExpectedValue(Duration.ofMinutes(5))
			.register(meterRegistry)
			.record(elapsedNanos, NANOSECONDS);
	}

	private Timer checkTimer(String check) {
		return Timer.builder(CHECK_DURATION_METRIC)
			.description("Time one preflight check took for one placement")
			.tag("check", check)
			.publishPercentileHistogram()
			.maximumExpectedValue(Duration.ofMinutes(5))
			.register(meterRegistry);
	}

	/**
	 * One line per placement, for measuring per library what the timer cannot: the borrowing system,
	 * whether a copy was chosen, and each check's time. Codes only, never the patron.
	 */
	static String summary(PlacePatronRequestCommand command, String outcome, long millis,
		List<TimedCheck> finished, String unfinishedCheck, List<CheckResult> results) {

		final var perCheck = new ArrayList<String>();
		finished.forEach(check -> perCheck.add(check.name() + "=" + check.millis() + "ms"));
		if (unfinishedCheck != null) {
			perCheck.add(unfinishedCheck + "=unfinished");
		}

		final var codes = results.stream()
			.filter(CheckResult::getFailed)
			.map(CheckResult::getFailureCode)
			.distinct()
			.toList();

		return "Preflight checks %s in %d ms: borrowingHostLms=%s copyChosen=%s expedited=%s codes=%s checks=%s"
			.formatted(outcome, millis,
				getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode),
				getValueOrNull(command, PlacePatronRequestCommand::getItem) != null,
				Boolean.TRUE.equals(getValueOrNull(command, PlacePatronRequestCommand::getIsExpeditedRequest)),
				codes, perCheck);
	}

	private static String nameOf(PreflightCheck check) {
		final var name = check.getClass().getSimpleName();
		final var generated = name.indexOf('$');

		return generated > 0 ? name.substring(0, generated) : name;
	}

	record TimedCheck(String name, long millis, List<CheckResult> results) {
	}

	private static boolean allPassed(List<CheckResult> results) {
		if (isEmpty(results)) {
			log.warn("No preflight check results returned");
			return true;
		}

		return results.stream().allMatch(CheckResult::getPassed);
	}

	private static List<FailedPreflightCheck> failedChecksOnly(List<CheckResult> reportedResults) {
		if (isEmpty(reportedResults)) {
			log.warn("No preflight check results returned");
			return emptyList();
		}

		return reportedResults.stream()
			.filter(CheckResult::getFailed)
			.map(FailedPreflightCheck::fromResult)
			.toList();
	}

	private Mono<List<CheckResult>> reportFailedChecksInEventLog(List<CheckResult> results,
		PlacePatronRequestCommand command) {

		return Flux.fromIterable(failedChecksOnly(results))
			.concatMap(result -> eventLogRepository.save(eventFrom(result, command)))
			.then(Mono.just(results));
	}

	/**
	 * A refused request never becomes a patron request, so this row is the only record that it
	 * was attempted, and the description alone said neither which cluster nor which library.
	 * The context goes in additional_data rather than the summary because event_summary is
	 * varchar(128) and these descriptions already reach 104 - anything prefixed truncates away
	 * the reason the request was refused. No patron identifier in either: this is a durable
	 * row, not a support ticket.
	 */
	private static Event eventFrom(FailedPreflightCheck failedCheck, PlacePatronRequestCommand command) {
		final var clusterId = getValueOrNull(command, PlacePatronRequestCommand::getCitation,
			PlacePatronRequestCommand.Citation::getBibClusterId);

		final var requestor = getValueOrNull(command, PlacePatronRequestCommand::getRequestor);

		final var agencyCode = getValueOrNull(requestor, PlacePatronRequestCommand.Requestor::getAgencyCode);
		final var homeLibraryCode = getValueOrNull(requestor, PlacePatronRequestCommand.Requestor::getHomeLibraryCode);
		final var hostLmsCode = getValueOrNull(requestor, PlacePatronRequestCommand.Requestor::getLocalSystemCode);

		final var code = getValue(failedCheck, FailedPreflightCheck::getCode, "UNKNOWN_CHECK");
		final var description = getValue(failedCheck, FailedPreflightCheck::getDescription, "");

		final var context = new HashMap<String, Object>();
		context.put("code", code);
		context.put("clusterId", getValue(clusterId, Object::toString, "unknown"));
		context.put("hostLmsCode", getValue(hostLmsCode, "unknown"));

		// EBSCO Locate sends no agency code. A home library code is the patron's local code
		// on that host LMS, usually but not always the agency code, so it keeps its own key
		if (agencyCode != null) {
			context.put("agencyCode", agencyCode);
		}
		else {
			context.put("homeLibraryCode", getValue(homeLibraryCode, "unknown"));
		}

		context.put("description", description);

		return Event.builder()
			.id(UUID.randomUUID())
			.type(FAILED_CHECK)
			.summary(truncate(description, 128))
			.additionalData(context)
			.build();
	}
}
