package org.olf.dcb.indexing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.olf.dcb.indexing.SharedIndexBackendInfo;
import org.olf.dcb.indexing.SharedIndexConfiguration;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Hooks;

@Isolated("Temporarily captures Reactor's global dropped-error hook")
class ElasticsearchRefreshErrorTests {

	private static final String INDEX = "shared-index";

	enum FailurePoint { READ_BEFORE_DISABLE, WRITE_TO_DISABLE, WRITE_TO_RESTORE }

	@ParameterizedTest
	@EnumSource(FailurePoint.class)
	@SuppressWarnings("unchecked")
	void shouldLogRefreshFailureWithItsCauseWithoutDroppingIt(FailurePoint point) {
		final var client = mock(ElasticsearchAsyncClient.class, RETURNS_DEEP_STUBS);
		final var read = new CompletableFuture<GetIndicesSettingsResponse>();
		final var write = new CompletableFuture<PutIndicesSettingsResponse>();
		when(client.indices().getSettings(any(Function.class))).thenReturn(read);
		when(client.indices().putSettings(any(Function.class))).thenReturn(write);
		final var service = new TestService(client);
		final var failure = new IOException("Connection is closed");
		final List<Throwable> dropped = new ArrayList<>();
		final var logger = (Logger) LoggerFactory.getLogger(ElasticsearchSharedIndexService.class);
		final var logs = new ListAppender<ILoggingEvent>();
		logs.start();
		logger.addAppender(logs);
		Hooks.onErrorDropped(dropped::add);

		try {
			if (point == FailurePoint.WRITE_TO_RESTORE) {
				service.rateThresholdClosedHook();
			} else {
				service.rateThresholdOpenHook();
			}
			// Complete after subscribe, as happens with a failed asynchronous HTTP request.
			if (point == FailurePoint.READ_BEFORE_DISABLE) {
				read.completeExceptionally(failure);
			} else {
				if (point == FailurePoint.WRITE_TO_DISABLE) {
					read.complete(GetIndicesSettingsResponse.of(response -> response
						.settings(INDEX + "-2", state -> state.settings(settings -> settings
							.index(index -> index.refreshInterval(time -> time.time("30s")))))));
				}
				write.completeExceptionally(failure);
			}

			assertTrue(dropped.isEmpty(), "Refresh failures must not reach the global Slack reporting hook");
			final var logged = logs.list.stream()
				.filter(event -> event.getFormattedMessage().startsWith("Unable to "))
				.findFirst().orElseThrow();
			assertTrue(logged.getFormattedMessage().contains(INDEX));
			assertTrue(logged.getFormattedMessage().contains(
				point == FailurePoint.WRITE_TO_RESTORE ? "restore" : "disable"));
			final var error = ((ThrowableProxy) logged.getThrowableProxy()).getThrowable();
			assertSame(failure, error.getCause(), "Keep the transport cause available in application logs");
			if (point != FailurePoint.READ_BEFORE_DISABLE) {
				assertEquals("Error in (ES) changeIndexSettings for " + INDEX + ": Connection is closed",
					error.getMessage());
			}
		} finally {
			Hooks.resetOnErrorDropped();
			logger.detachAppender(logs);
			logs.stop();
		}
	}

	private static final class TestService extends ElasticsearchSharedIndexService {
		TestService(ElasticsearchAsyncClient client) {
			super(new SharedIndexConfiguration(INDEX, Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
				client, null, null, null, new SharedIndexBackendInfo());
		}

		@Override
		protected void initializeQueue() {
			// Exercise the refresh callbacks without starting the unrelated indexing queue.
		}
	}
}
