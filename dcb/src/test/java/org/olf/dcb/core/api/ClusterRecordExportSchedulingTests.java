package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.storage.BibRepository;

import io.micronaut.serde.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

class ClusterRecordExportSchedulingTests {
	@Test
	void serializesAfterR2dbcLikeEmissionOnBlockingExecutor() throws Exception {
		final var eventExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("cluster-test-r2dbc").factory());
		final var blockingExecutor = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("cluster-test-blocking").factory());

		try {
			final var record = mock(BibRecord.class);
			when(record.getId()).thenReturn(UUID.randomUUID());
			final var repository = mock(BibRepository.class);
			when(repository.findAllByContributesToId(any())).thenReturn(
				Flux.just(record).subscribeOn(Schedulers.fromExecutor(eventExecutor)));

			final var serializationThread = new AtomicReference<String>();
			final var objectMapper = mock(ObjectMapper.class);
			when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
				serializationThread.set(Thread.currentThread().getName());
				return "{}";
			});

			final var controller = new ClusterRecordController(repository,
				mock(ProcessAuditService.class), objectMapper,
				mock(RecordClusteringService.class), blockingExecutor);

			controller.exportMembers(UUID.randomUUID()).block();

			assertThat(serializationThread.get(), startsWith("cluster-test-blocking"));
		}
		finally {
			eventExecutor.shutdownNow();
			blockingExecutor.shutdownNow();
		}
	}
}
