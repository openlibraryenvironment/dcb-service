package org.olf.dcb.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLmsProcessingStateCount;
import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.core.model.RecordCountSummary;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordImportJob;
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
import reactor.test.scheduler.VirtualTimeScheduler;

// The service is built after the virtual clock is installed, because the timeout and the cache
// take their scheduler when the field is assembled
class ImportIngestDetailsServiceTests {
	private final UUID hostLmsId = UUID.randomUUID();

	private SourceRecordRepository sourceRecordRepository;
	private BibRepository bibRepository;
	private HostLmsRepository hostLmsRepository;
	private VirtualTimeScheduler clock;

	@BeforeEach
	void beforeEach() {
		clock = VirtualTimeScheduler.getOrSet();

		sourceRecordRepository = mock(SourceRecordRepository.class);
		bibRepository = mock(BibRepository.class);
		when(bibRepository.getIngestReport()).thenReturn(Flux.<RecordCountSummary>empty());

		hostLmsRepository = mock(HostLmsRepository.class);
		when(hostLmsRepository.queryAll()).thenReturn(Flux.just(
			DataHostLms.builder().id(hostLmsId).code("cache-host-lms").name("Cache").build()));
	}

	@AfterEach
	void afterEach() {
		VirtualTimeScheduler.reset();
	}

	// In an API test SourceRecordService does not exist (its package is @Requires(notEnv = TEST)),
	// so ingestEnabled is never set there; hence a mocked ingest source here
	@Test
	@SuppressWarnings("unchecked")
	void shouldReadTheIngestFlagFreshWhileTheCountsAreShared() {
		// Arrange
		when(sourceRecordRepository.getProcessingStateCountsByHostLms())
			.thenReturn(Flux.just(new HostLmsProcessingStateCount(hostLmsId, "SUCCESS", 3L)));

		when(hostLmsRepository.queryAll()).thenReturn(Flux.just(DataHostLms.builder()
			.id(hostLmsId).code("cache-host-lms").name("Cache")
			.lmsClientClass(SierraLmsClient.class.getName())
			.build()));

		final var context = mock(BeanContext.class);
		doReturn(mock(SierraLmsClient.class)).when(context)
			.createBean(ArgumentMatchers.<Class<Object>>any(), ArgumentMatchers.<Object>any());

		final var job = mock(SourceRecordImportJob.class);
		when(job.getId()).thenReturn(UUID.randomUUID());

		final var sourceRecordService = mock(SourceRecordService.class);
		when(sourceRecordService.createJobInstanceForSource(any(), eq(true))).thenReturn(Mono.just(job));
		when(sourceRecordService.isIngestEnabled(any())).thenReturn(true, false);

		final var sourceRecordServiceProvider = (BeanProvider<SourceRecordService>) mock(BeanProvider.class);
		when(sourceRecordServiceProvider.get()).thenReturn(sourceRecordService);

		final var jobCheckpointRepository = mock(JobCheckpointRepository.class);
		when(jobCheckpointRepository.findCheckpointByJobId(any())).thenReturn(Mono.empty());

		final var service = service(context, jobCheckpointRepository, sourceRecordServiceProvider);

		// Act: ingest is turned off between the two calls
		final var before = call(service).value().get().get(0);
		clock.advanceTimeBy(Duration.ofMinutes(1));
		final var after = call(service).value().get().get(0);

		// Assert
		assertThat(before.get("ingestEnabled"), is(true));
		assertThat(after.get("ingestEnabled"), is(false));
		assertThat(after.get("countedAt"), is(before.get("countedAt")));
		verify(sourceRecordRepository, times(1)).getProcessingStateCountsByHostLms();
	}

	// Mocked because Postgres often returns the groups already sorted, so a database test passes
	// with or without the sort
	@Test
	@SuppressWarnings("unchecked")
	void shouldListProcessingStatesInStateOrderWithNoStateLast() {
		// Arrange
		when(sourceRecordRepository.getProcessingStateCountsByHostLms()).thenReturn(Flux.just(
			new HostLmsProcessingStateCount(hostLmsId, "SUCCESS", 3L),
			new HostLmsProcessingStateCount(hostLmsId, null, 4L),
			new HostLmsProcessingStateCount(hostLmsId, "FAILURE", 1L),
			new HostLmsProcessingStateCount(hostLmsId, "PROCESSING_REQUIRED", 2L)));

		// Act
		final var details = call(service()).value().get().get(0);

		// Assert
		assertThat(((List<RecordCount>) details.get("processStates")).stream().map(RecordCount::getValue).toList(),
			contains("FAILURE", "PROCESSING_REQUIRED", "SUCCESS", null));
	}

	@Test
	void shouldFailAStuckCountSoALaterRequestCanStartAnother() {
		// Arrange
		when(sourceRecordRepository.getProcessingStateCountsByHostLms()).thenReturn(
			Flux.<HostLmsProcessingStateCount>never(),
			Flux.just(new HostLmsProcessingStateCount(hostLmsId, "SUCCESS", 3L)));

		final var service = service();

		// Act
		final var stuck = call(service);
		clock.advanceTimeBy(HostLmsService.CATALOGUE_COUNT_TIMEOUT);

		clock.advanceTimeBy(HostLmsService.FAILED_COUNT_TTL.plusSeconds(1));
		final var later = call(service);

		// Assert
		assertThat(stuck.error().get(), instanceOf(TimeoutException.class));
		assertThat(sourceRecordCountIn(later), is(3L));
		verify(sourceRecordRepository, times(2)).getProcessingStateCountsByHostLms();
	}

	@Test
	void shouldHoldAFailedCountBrieflyRatherThanRerunItOnEveryRequest() {
		// Arrange
		when(sourceRecordRepository.getProcessingStateCountsByHostLms()).thenReturn(
			Flux.<HostLmsProcessingStateCount>error(new IllegalStateException("count failed")),
			Flux.just(new HostLmsProcessingStateCount(hostLmsId, "SUCCESS", 3L)));

		final var service = service();

		// Act
		final var first = call(service);

		clock.advanceTimeBy(Duration.ofSeconds(10));
		final var soonAfter = call(service);
		verify(sourceRecordRepository, times(1)).getProcessingStateCountsByHostLms();

		clock.advanceTimeBy(HostLmsService.FAILED_COUNT_TTL);
		final var afterTheHold = call(service);

		// Assert
		assertThat(first.error().get(), instanceOf(IllegalStateException.class));
		assertThat(soonAfter.error().get(), instanceOf(IllegalStateException.class));
		assertThat(afterTheHold.error().get(), is(nullValue()));
		assertThat(sourceRecordCountIn(afterTheHold), is(3L));
		verify(sourceRecordRepository, times(2)).getProcessingStateCountsByHostLms();
	}

	private record Outcome(AtomicReference<List<Map<String, Object>>> value,
		AtomicReference<Throwable> error) {
	}

	private static Outcome call(HostLmsService service) {
		final var outcome = new Outcome(new AtomicReference<>(), new AtomicReference<>());
		service.getAllImportIngestDetails().subscribe(outcome.value()::set, outcome.error()::set);
		return outcome;
	}

	private static Object sourceRecordCountIn(Outcome outcome) {
		assertThat(outcome.value().get(), notNullValue());
		return outcome.value().get().get(0).get("sourceRecordCount");
	}

	@SuppressWarnings("unchecked")
	private HostLmsService service() {
		return service(mock(BeanContext.class), mock(JobCheckpointRepository.class),
			(BeanProvider<SourceRecordService>) mock(BeanProvider.class));
	}

	private HostLmsService service(BeanContext context, JobCheckpointRepository jobCheckpointRepository,
		BeanProvider<SourceRecordService> sourceRecordServiceProvider) {

		return new HostLmsService(mock(BibRecordService.class), context, hostLmsRepository,
			mock(RawSourceRepository.class), sourceRecordRepository, jobCheckpointRepository,
			bibRepository, sourceRecordServiceProvider);
	}
}
