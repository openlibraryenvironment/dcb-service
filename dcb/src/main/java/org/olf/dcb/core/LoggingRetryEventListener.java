package org.olf.dcb.core;


import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.olf.dcb.core.interaction.sierra.SierraReadTimeoutProblem;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.svc.AlarmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.retry.event.RetryEventListener;
import jakarta.inject.Singleton;
import services.k_int.utils.UUIDUtils;

@Singleton
public class LoggingRetryEventListener implements RetryEventListener {

	static final Logger log = LoggerFactory.getLogger(LoggingRetryEventListener.class);
	private final AlarmsService alarmsService;
	private final Counter alarmPersistenceFailures;

	public LoggingRetryEventListener(AlarmsService alarmsService, MeterRegistry meterRegistry) {
		this.alarmsService = alarmsService;
		this.alarmPersistenceFailures = Counter.builder("dcb.alarm.persistence.failures")
			.description("Alarm writes which could not be persisted")
			.tag("source", "sierra-read-timeout")
			.register(meterRegistry);
	}

	@Override
	public void onApplicationEvent(RetryEvent retry) {

		final var source = retry.getSource();
		final var retryState = retry.getRetryState();
		final var exhausted = retryState.currentAttempt() == retryState.getMaxAttempts();
		if (exhausted && retry.getThrowable() instanceof SierraReadTimeoutProblem timeout) {
			reportSierraReadTimeout(timeout, source.getName(), retryState.currentAttempt());
			return;
		}
		if (!log.isInfoEnabled()) {
			return;
		}

		final String args = Arrays.stream(
				source.getParameterValues())
			.filter(Objects::nonNull)
			.map(o -> Objects.toString(o, null))
			.collect(Collectors.joining(", "));

		if (log.isDebugEnabled()) {
			log.atDebug().log("Retry #{} for \"{}\" {}::{}( {} )",
				retryState.currentAttempt(),
				retry.getThrowable().getMessage(),
				source.getDeclaringType().getSimpleName(),
				source.getName(), args);
			
		} else if (log.isInfoEnabled()) {
			log.atInfo().log("Retry #{} for \"{}\" {}::{}",
				retryState.currentAttempt(),
				retry.getThrowable().getMessage(),
				source.getDeclaringType().getSimpleName(),
				source.getName());
		}
		
		if (exhausted) {
			retry.getThrowable().printStackTrace();
			log.atError()
				.setCause(retry.getThrowable())
			  .log("Exhausted retry count ({}), for {}::{}", 
					retryState.currentAttempt(),
					source.getDeclaringType().getSimpleName(),
					source.getName());
			
		}
	}

	private void reportSierraReadTimeout(SierraReadTimeoutProblem timeout, String operation,
		int attempts) {

		final var alarmCode = "ILS." + timeout.getHostLmsCode() + ".SIERRA_READ_TIMEOUT";
		log.warn("Sierra Host LMS [{}] exhausted {} attempts for {} after read timeout: {} {}",
			timeout.getHostLmsCode(), attempts, operation, timeout.getRequestMethod(),
			timeout.getRequestPath());

		alarmsService.raiseAccumulating(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.expires(Instant.now().plus(Duration.ofDays(5)))
				.build(),
			"timedOutRequests", operation + ": " + timeout.getRequestMethod() + " " + timeout.getRequestPath())
			.subscribe(
				ignored -> { },
				error -> {
					alarmPersistenceFailures.increment();
					log.warn("Unable to persist Sierra timeout alarm [{}] for Host LMS [{}], {} {}: {}: {}",
						alarmCode, timeout.getHostLmsCode(), timeout.getRequestMethod(),
						timeout.getRequestPath(), error.getClass().getSimpleName(), error.getMessage());
				});
	}
}
