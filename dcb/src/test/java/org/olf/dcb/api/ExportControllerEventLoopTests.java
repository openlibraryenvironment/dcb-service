package org.olf.dcb.api;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.export.ExportService;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// One event-loop thread makes a blocking controller regression reproduce as a bounded test failure.
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "1")
class ExportControllerEventLoopTests {
	private static final String ACCESS_TOKEN = "export-event-loop-admin-token";
	private static final AtomicReference<EventLoopGroupRegistry> EVENT_LOOPS =
		new AtomicReference<>();

	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private EventLoopGroupRegistry eventLoopGroups;

	@MockBean(ExportService.class)
	ExportService exportService() {
		// Isolate the transport boundary while retaining a downstream synchronous wait.
		final var service = mock(ExportService.class);

		doAnswer(invocation -> {
			final var completion = new CountDownLatch(1);
			EVENT_LOOPS.get().getDefaultEventLoopGroup().next().execute(completion::countDown);

			if (!completion.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Event-loop completion was starved by the request");
			}

			return null;
		}).when(service).export(any(), any());

		return service;
	}

	@BeforeAll
	void registerAdministrator() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "export-event-loop-admin",
			List.of(ADMINISTRATOR));
		EVENT_LOOPS.set(eventLoopGroups);
	}

	@Test
	void exportCompletesWithOneNettyEventLoopThread() {
		// This fails if the downstream wait prevents completion queued on the sole event loop.
		assertTimeoutPreemptively(ofSeconds(10), () -> {
			final var response = client.toBlocking().exchange(
				HttpRequest.GET("/export/?ids=00000000-0000-0000-0000-000000000001&agencyCodes=")
					.bearerAuth(ACCESS_TOKEN),
				String.class);

			assertEquals(HttpStatus.OK, response.status());
		});
	}
}
