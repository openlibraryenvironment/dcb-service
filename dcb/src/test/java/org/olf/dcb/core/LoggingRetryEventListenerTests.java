package org.olf.dcb.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.olf.dcb.core.interaction.sierra.SierraReadTimeoutProblem;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.svc.AlarmsService;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.event.RetryEvent;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LoggingRetryEventListenerTests {
	@Mock
	private AlarmsService alarmsService;
	@Mock
	private MethodInvocationContext<?, ?> invocation;
	@Mock
	private RetryState retryState;

	@Test
	void raisesOneAlarmForAnExhaustedSierraReadTimeout() {
		final var timeout = new SierraReadTimeoutProblem(ReadTimeoutException.TIMEOUT_EXCEPTION,
			HttpRequest.POST("/iii/sierra-api/v6/token", ""), "JEFFERSON_COUNTY");
		when(retryState.currentAttempt()).thenReturn(3);
		when(retryState.getMaxAttempts()).thenReturn(3);
		when(invocation.getName()).thenReturn("items");
		when(alarmsService.raiseAccumulating(any(), eq("timedOutRequests"),
			eq("items: POST /iii/sierra-api/v6/token"))).thenReturn(Mono.empty());

		new LoggingRetryEventListener(alarmsService)
			.onApplicationEvent(new RetryEvent(invocation, retryState, timeout));

		final ArgumentCaptor<Alarm> alarm = ArgumentCaptor.forClass(Alarm.class);
		verify(alarmsService).raiseAccumulating(alarm.capture(), eq("timedOutRequests"),
			eq("items: POST /iii/sierra-api/v6/token"));
		assertThat(alarm.getValue().getCode(), is("ILS.JEFFERSON_COUNTY.SIERRA_READ_TIMEOUT"));
		assertThat(alarm.getValue().getExpires().isAfter(Instant.now()), is(true));
	}

	@Test
	void doesNotRaiseAnAlarmBeforeSierraRetriesAreExhausted() {
		final var timeout = new SierraReadTimeoutProblem(ReadTimeoutException.TIMEOUT_EXCEPTION,
			HttpRequest.POST("/iii/sierra-api/v6/token", ""), "JEFFERSON_COUNTY");
		when(retryState.currentAttempt()).thenReturn(1);
		when(retryState.getMaxAttempts()).thenReturn(3);
		when(invocation.getParameterValues()).thenReturn(new Object[0]);
		doReturn(LoggingRetryEventListener.class).when(invocation).getDeclaringType();
		when(invocation.getName()).thenReturn("items");

		new LoggingRetryEventListener(alarmsService)
			.onApplicationEvent(new RetryEvent(invocation, retryState, timeout));

		verifyNoInteractions(alarmsService);
	}
}
