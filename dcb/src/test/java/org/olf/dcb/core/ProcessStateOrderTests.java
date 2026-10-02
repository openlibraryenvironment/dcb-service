package org.olf.dcb.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLmsProcessingStateCount;
import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.JobCheckpointRepository;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.storage.SourceRecordRepository;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// The repository is mocked because Postgres often returns the groups already sorted, so a test
// against the database passes with or without the sort it is meant to prove
class ProcessStateOrderTests {
	@Test
	@SuppressWarnings("unchecked")
	void shouldListProcessingStatesInStateOrderWithNoStateLast() {
		// Arrange
		final var hostLmsId = UUID.randomUUID();
		final var hostLms = DataHostLms.builder().id(hostLmsId).code("order-host-lms").name("Order").build();

		final var hostLmsRepository = mock(HostLmsRepository.class);
		when(hostLmsRepository.findById(hostLmsId)).thenReturn(Mono.just(hostLms));

		final var sourceRecordRepository = mock(SourceRecordRepository.class);
		when(sourceRecordRepository.getProcessingStateCountsForHostLms(hostLmsId)).thenReturn(Flux.just(
			new HostLmsProcessingStateCount(hostLmsId, "SUCCESS", 3L),
			new HostLmsProcessingStateCount(hostLmsId, null, 4L),
			new HostLmsProcessingStateCount(hostLmsId, "FAILURE", 1L),
			new HostLmsProcessingStateCount(hostLmsId, "PROCESSING_REQUIRED", 2L)));

		final var bibRepository = mock(BibRepository.class);
		when(bibRepository.getCountForHostLms(any())).thenReturn(Mono.just(0L));

		final var service = new HostLmsService(mock(BibRecordService.class), mock(BeanContext.class),
			hostLmsRepository, mock(RawSourceRepository.class), sourceRecordRepository,
			mock(JobCheckpointRepository.class), bibRepository,
			(BeanProvider<SourceRecordService>) mock(BeanProvider.class));

		// Act
		final var details = singleValueFrom(service.getImportIngestDetails(hostLmsId));

		// Assert
		final var states = new ArrayList<>((List<RecordCount>) details.get("processStates"));

		assertThat(states.stream().map(RecordCount::getValue).toList(),
			contains("FAILURE", "PROCESSING_REQUIRED", "SUCCESS", null));
	}
}
