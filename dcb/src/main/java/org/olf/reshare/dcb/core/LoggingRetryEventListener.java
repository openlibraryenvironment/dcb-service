package org.olf.reshare.dcb.core;


import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parallel;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.retry.event.RetryEventListener;
import jakarta.inject.Singleton;

@Singleton
@Parallel
public class LoggingRetryEventListener implements RetryEventListener {

	static final Logger log = LoggerFactory.getLogger(LoggingRetryEventListener.class);

	@Override
	public void onApplicationEvent(RetryEvent retry) {

		if (!log.isInfoEnabled()) {
			return;
		}

		final var source = retry.getSource();
		final String args = Arrays.stream(
				source.getParameterValues())
			.filter(Objects::nonNull)
			.map(o -> Objects.toString(o, null))
			.collect(Collectors.joining(", "));

		log.info("Retry #{} for {}::{}( {} )",
			retry.getRetryState().currentAttempt(),
			source.getDeclaringType().getSimpleName(),
			source.getName(), args);
		log.debug("Caused by: {}", retry.getThrowable());
	}
}
