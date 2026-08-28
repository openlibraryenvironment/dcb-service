package org.olf.dcb.request.lifecycle.ncip;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// One event loop makes a missing NCIP transport dispatch reproduce as a bounded failure.
@Property(name = "micronaut.netty.event-loops.default.num-threads", value = "1")
class NcipControllerEventLoopTests {
	private static final AtomicReference<EventLoopGroupRegistry> EVENT_LOOPS =
		new AtomicReference<>();

	@Inject
	@Client("/")
	private HttpClient client;
	@Inject
	private EventLoopGroupRegistry eventLoopGroups;

	@MockBean(NcipPeerAuthGuard.class)
	NcipPeerAuthGuard peerAuthGuard() {
		final var guard = mock(NcipPeerAuthGuard.class);
		doAnswer(invocation -> {
			final var completion = new CountDownLatch(1);
			EVENT_LOOPS.get().getDefaultEventLoopGroup().next().execute(completion::countDown);
			if (!completion.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Event-loop completion was starved by NCIP parsing");
			}
			return Mono.just(Optional.empty());
		}).when(guard).problem(any(), any());
		return guard;
	}

	@MockBean(InboundLifecycleMessageHandler.class)
	InboundLifecycleMessageHandler messageHandler() {
		final var handler = mock(InboundLifecycleMessageHandler.class);
		when(handler.handle(any())).thenReturn(Mono.just(new RequestWorkflowContext()));
		return handler;
	}

	@BeforeAll
	void captureEventLoop() {
		EVENT_LOOPS.set(eventLoopGroups);
	}

	@Test
	void ncipCompletesWithOneNettyEventLoopThread() {
		assertTimeoutPreemptively(ofSeconds(10), () -> {
			final var response = client.toBlocking().exchange(
				HttpRequest.POST("/ncip/v2_02", NcipControllerTests.validItemShipped())
					.contentType(MediaType.APPLICATION_XML_TYPE),
				String.class);

			assertEquals(HttpStatus.OK, response.status());
		});
	}
}
