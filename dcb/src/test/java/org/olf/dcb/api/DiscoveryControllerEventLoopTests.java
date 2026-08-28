package org.olf.dcb.api;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
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
// One event loop makes synchronous assertion verification regressions fail deterministically.
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "1")
class DiscoveryControllerEventLoopTests {
	private static final String ACCESS_TOKEN = "discovery-event-loop-token";
	private static final AtomicReference<EventLoopGroupRegistry> EVENT_LOOPS =
		new AtomicReference<>();

	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private EventLoopGroupRegistry eventLoopGroups;

	@MockBean(PatronAssertionVerifier.class)
	PatronAssertionVerifier assertionVerifier() {
		final var verifier = mock(PatronAssertionVerifier.class);
		doAnswer(invocation -> {
			final var completion = new CountDownLatch(1);
			EVENT_LOOPS.get().getDefaultEventLoopGroup().next().execute(completion::countDown);
			if (!completion.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Event-loop completion was starved by assertion verification");
			}
			return new PatronAssertion("test-system", "test-patron", "test-discovery");
		}).when(verifier).verify(any());
		return verifier;
	}

	@BeforeAll
	void registerDiscoveryService() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "discovery-event-loop",
			List.of(DISCOVERY_SERVICE));
		EVENT_LOOPS.set(eventLoopGroups);
	}

	@Test
	void patronListCompletesWithOneNettyEventLoopThread() {
		assertTimeoutPreemptively(ofSeconds(10), () -> {
			final var response = client.toBlocking().exchange(
				HttpRequest.GET("/discovery/requests?page=0&size=1")
					.bearerAuth(ACCESS_TOKEN),
				String.class);

			assertEquals(HttpStatus.OK, response.status());
		});
	}
}
