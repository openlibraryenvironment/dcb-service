package org.olf.dcb.test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
class R2dbcPoolLifecycleTests {
	@Inject
	private ConnectionFactory connectionFactory;

	@Test
	void evictsReleasedConnections() {
		assertThat(connectionFactory, is(instanceOf(ConnectionPool.class)));

		final var pool = (ConnectionPool) connectionFactory;
		Mono.usingWhen(Mono.from(pool.create()), connection -> Mono.empty(),
			connection -> Mono.from(connection.close()))
			.block();

		await().atMost(5, SECONDS).until(() ->
			pool.getMetrics().orElseThrow().allocatedSize(), is(0));
	}
}
