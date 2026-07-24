package org.olf.dcb.stats;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.hazelcast.core.HazelcastInstance;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Phase 1: event counts moved from the unbounded in-heap stat_counters map onto
 * Micrometer meters. These are pure unit tests — notifyEvent/notifyTimedEvent never
 * touch Hazelcast, so a mock instance is enough.
 */
class StatsServiceTest {

	@Test
	void notifyEventIncrementsATaggedCounter() {
		final var registry = new SimpleMeterRegistry();
		final var stats = new StatsService(Mockito.mock(HazelcastInstance.class), registry);

		stats.notifyEvent("BibInsert", "SIERRA-A");
		stats.notifyEvent("BibInsert", "SIERRA-A");
		stats.notifyEvent("BibInsert", "POLARIS-B");

		assertThat("counts are separated by the context tag",
			registry.counter("dcb.stats.events", "event", "BibInsert", "context", "SIERRA-A").count(),
			is(2.0));
		assertThat(
			registry.counter("dcb.stats.events", "event", "BibInsert", "context", "POLARIS-B").count(),
			is(1.0));
	}

	@Test
	void notifyTimedEventRecordsToATimer() {
		final var registry = new SimpleMeterRegistry();
		final var stats = new StatsService(Mockito.mock(HazelcastInstance.class), registry);

		stats.notifyTimedEvent("IngestRecord", "SIERRA-A", 42);

		assertThat(
			registry.timer("dcb.stats.timed", "event", "IngestRecord", "context", "SIERRA-A").count(),
			is(1L));
	}
}
