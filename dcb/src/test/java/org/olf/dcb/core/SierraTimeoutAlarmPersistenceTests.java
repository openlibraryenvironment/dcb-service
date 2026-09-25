package org.olf.dcb.core;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.sierra.SierraReadTimeoutProblem;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.storage.AlarmRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.event.RetryEvent;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@MicronautTest(contextBuilder = DcbTestContainerContextBuilder.class, transactional = false)
class SierraTimeoutAlarmPersistenceTests {
	private static final String ALARM_CODE = "ILS.JEFFERSON_COUNTY.SIERRA_READ_TIMEOUT";

	@Inject
	private LoggingRetryEventListener retryEventListener;
	@Inject
	private AlarmRepository alarmRepository;
	@Inject
	private R2dbcOperations r2dbcOperations;
	@Inject
	private R2dbcReactorTransactionOperations transactionOperations;
	@Inject
	private StatusCodeRepository statusCodeRepository;
	@Inject
	private DefaultDataSourceConnectionOperations jdbcConnectionOperations;
	@Value("${dcb.r2dbc.legacy-transaction-operations.enabled}")
	private boolean legacyTransactionOperationsEnabled;

	private UUID independentlyCommittedStatusCodeId;

	@AfterEach
	void cleanUp() {
		Mono.from(alarmRepository.deleteByCode(ALARM_CODE)).block();
		if (independentlyCommittedStatusCodeId != null) {
			Mono.from(statusCodeRepository.delete(independentlyCommittedStatusCodeId)).block();
		}
	}

	@Test
	void requiresNewUsesAnIndependentR2dbcTransactionAlongsideJdbc() {
		final UUID rolledBackStatusCodeId = UUID.randomUUID();
		independentlyCommittedStatusCodeId = UUID.randomUUID();

		Mono.from(transactionOperations.withTransactionMono(status ->
			Mono.from(statusCodeRepository.save(statusCode(rolledBackStatusCodeId)))
				.then(transactionOperations.withTransactionMono(
					TransactionDefinition.of(TransactionDefinition.Propagation.REQUIRES_NEW),
					newStatus -> Mono.from(statusCodeRepository.save(statusCode(independentlyCommittedStatusCodeId)))))
				.then(Mono.error(new IllegalStateException("force parent rollback")))))
			.onErrorResume(IllegalStateException.class, ignored -> Mono.empty())
			.block();

		assertThat(Mono.from(statusCodeRepository.findById(rolledBackStatusCodeId)).block(), nullValue());
		assertThat(Mono.from(statusCodeRepository.findById(independentlyCommittedStatusCodeId)).block(), notNullValue());
		final Integer jdbcResult = jdbcConnectionOperations.executeRead(status -> {
			try (var statement = status.getConnection().createStatement();
				var result = statement.executeQuery("SELECT 1")) {
				result.next();
				return result.getInt(1);
			} catch (SQLException error) {
				throw new IllegalStateException("JDBC transaction probe failed", error);
			}
		});
		assertThat(jdbcResult, is(1));
	}

	private StatusCode statusCode(UUID id) {
		return StatusCode.builder()
			.id(id)
			.model("TransactionProbe")
			.code("transaction-probe")
			.tracked(false)
			.build();
	}

	@Test
	void persistsTheSierraAlarmOutsideTheFailedCallerTransaction() {
		assertThat("the configured transaction implementation must be selected",
			transactionOperations.getClass().getSimpleName(),
			is(legacyTransactionOperationsEnabled
				? "ReplacementR2dbcReactorTransactionOperations"
				: "DefaultR2dbcReactorTransactionOperations"));

		final var retryEvent = exhaustedSierraTimeout();
		Mono.from(r2dbcOperations.withTransaction(status -> {
			retryEventListener.onApplicationEvent(retryEvent);
			return Mono.<Void>error(new IllegalStateException("force parent rollback"));
		}))
			.onErrorResume(IllegalStateException.class, ignored -> Mono.empty())
			.block();

		await().atMost(5, SECONDS).untilAsserted(() -> {
			final Alarm alarm = Mono.from(alarmRepository.findByCode(ALARM_CODE)).block();
			assertThat(alarm, notNullValue());
			assertThat(alarm.getCode(), is(ALARM_CODE));
			assertThat((Iterable<?>) alarm.getAlarmDetails().get("timedOutRequests"),
				contains("items: POST /iii/sierra-api/v6/token"));
		});
	}

	private RetryEvent exhaustedSierraTimeout() {
		final MethodInvocationContext<?, ?> invocation = org.mockito.Mockito.mock(MethodInvocationContext.class);
		final RetryState retryState = org.mockito.Mockito.mock(RetryState.class);
		when(invocation.getName()).thenReturn("items");
		when(retryState.currentAttempt()).thenReturn(3);
		when(retryState.getMaxAttempts()).thenReturn(3);

		return new RetryEvent(invocation, retryState,
			new SierraReadTimeoutProblem(ReadTimeoutException.TIMEOUT_EXCEPTION,
				HttpRequest.POST("/iii/sierra-api/v6/token", ""), "JEFFERSON_COUNTY"));
	}
}
