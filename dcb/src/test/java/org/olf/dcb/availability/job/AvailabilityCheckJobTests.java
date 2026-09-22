package org.olf.dcb.availability.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.indexing.SharedIndexService;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.operations.OperationsService;
import org.olf.dcb.storage.BibAvailabilityCountRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.jobs.ReactiveJobRunnerService;

class AvailabilityCheckJobTests {
	private final BibAvailabilityCountRepository counts = mock(BibAvailabilityCountRepository.class);
	private final SharedIndexService sharedIndex = mock(SharedIndexService.class);
	private final AvailabilityCheckJobConfig config = mock(AvailabilityCheckJobConfig.class);
	private final LiveAvailabilityService liveAvailability = mock(LiveAvailabilityService.class);
	private final BibRecordService bibRecords = mock(BibRecordService.class);
	private AvailabilityCheckJob job;

	@BeforeEach
	void setUp() {
		when(config.getRecheckGracePeriod()).thenReturn(Duration.ofDays(7));

		job = new AvailabilityCheckJob(
			liveAvailability,
			sharedIndex,
			bibRecords,
			mock(LocationToAgencyMappingService.class),
			mock(HostLmsService.class),
			counts,
			mock(ReactiveJobRunnerService.class),
			mock(ReactorFederatedLockService.class),
			mock(OperationsService.class),
			config);
	}

	@Test
	void emptyRemotePublisherProducesADurableRetryOutcome() {
		final var bib = bib();
		final var concurrency = mock(AvailabilityCheckJobConfig.Concurrency.class);
		final var saved = ArgumentCaptor.forClass(BibAvailabilityCount.class);
		when(config.getConcurrency()).thenReturn(concurrency);
		when(concurrency.getInstanceWide()).thenReturn(Optional.of(1));
		when(concurrency.getPerSource()).thenReturn(1);
		when(bibRecords.findAllByIdIn(any())).thenReturn(Flux.just(bib));
		when(liveAvailability.fetchBibAvailabilityForBackfill(any(), any(), any())).thenReturn(Mono.empty());
		doAnswer(invocation -> Mono.just(invocation.getArgument(0)))
			.when(counts).saveOrUpdate(any());
		clearInvocations(counts);

		StepVerifier.create(job.checkClusterAvailability(List.of(bib.getId())))
			.expectNextMatches(result -> result.containsKey(bib.getId().toString()))
			.verifyComplete();

		verify(counts).saveOrUpdate(saved.capture());
		assertThat(saved.getValue().getMappingResult(),
			containsString("No availability report returned"));
		verify(counts, never()).deleteAllByBibIdAndHostLmsAndIdNotIn(any(), any(), any());
	}

	@Test
	void completeEmptyReportRemovesStaleCounts() {
		final var bib = bib();
		final var concurrency = mock(AvailabilityCheckJobConfig.Concurrency.class);
		when(config.getConcurrency()).thenReturn(concurrency);
		when(concurrency.getInstanceWide()).thenReturn(Optional.of(1));
		when(concurrency.getPerSource()).thenReturn(1);
		when(bibRecords.findAllByIdIn(any())).thenReturn(Flux.just(bib));
		when(liveAvailability.fetchBibAvailabilityForBackfill(any(), any(), any()))
			.thenReturn(Mono.just(AvailabilityReport.emptyReport()));
		doAnswer(invocation -> Mono.just(invocation.getArgument(0)))
			.when(counts).saveOrUpdate(any());
		doReturn(Mono.just(1L)).when(counts)
			.deleteAllByBibIdAndHostLmsAndIdNotIn(any(), any(), any());
		clearInvocations(counts);

		StepVerifier.create(job.checkClusterAvailability(List.of(bib.getId())))
			.expectNextMatches(result -> result.containsKey(bib.getId().toString()))
			.verifyComplete();

		verify(counts).deleteAllByBibIdAndHostLmsAndIdNotIn(
			eq(bib.getId()), eq(bib.getSourceSystemId()), any());
	}

	@Test
	void itemsWithoutLocationCodesProduceADurableRetryOutcome() {
		final var bib = bib();
		final var saved = ArgumentCaptor.forClass(BibAvailabilityCount.class);
		doAnswer(invocation -> Mono.just(invocation.getArgument(0)))
			.when(counts).saveOrUpdate(any());
		clearInvocations(counts);

		final var report = AvailabilityReport.ofItems(List.of(
			Item.builder().localId("missing-location").build(),
			Item.builder().localId("blank-location")
				.location(Location.builder().code(" ").build())
				.build()));

		StepVerifier.create(job.updateCountsForSingleBibAvailability(bib, report))
			.expectNext(bib.getContributesTo().getId())
			.verifyComplete();

		verify(counts).saveOrUpdate(saved.capture());
		final var outcome = saved.getValue();
		assertThat(outcome.getBibId(), is(bib.getId()));
		assertThat(outcome.getRemoteLocationCode(), is(nullValue()));
		assertThat(outcome.getCount(), is(0));
		assertThat(outcome.getStatus(), is(Status.UNMAPPED));
		assertThat(outcome.getMappingResult(), containsString("2 item(s) without location codes"));
		assertThat(outcome.getGracePeriodEnd() != null, is(true));
		verify(sharedIndex).add(bib.getContributesTo().getId());
	}

	@Test
	void countPersistenceFailureFailsTheUpdateAndDoesNotQueueIndexing() {
		final var expected = new IllegalStateException("database unavailable");
		doReturn(Mono.error(expected)).when(counts).saveOrUpdate(any());
		clearInvocations(counts);

		StepVerifier.create(job.updateCountsForSingleBibAvailability(
			bib(), AvailabilityReport.ofItems(List.of(Item.builder().build()))))
			.expectErrorMatches(error -> error == expected)
			.verify();

		verify(sharedIndex, never()).add(any());
	}

	private static BibRecord bib() {
		return BibRecord.builder()
			.id(UUID.randomUUID())
			.sourceSystemId(UUID.randomUUID())
			.sourceRecordId("source-record")
			.contributesTo(ClusterRecord.builder().id(UUID.randomUUID()).build())
			.build();
	}
}
