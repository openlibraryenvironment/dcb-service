package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.request.fulfilment.PatronRequestPreflightChecksService.CHECK_DURATION_METRIC;
import static org.olf.dcb.request.fulfilment.PatronRequestPreflightChecksService.DURATION_METRIC;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.request.fulfilment.PatronRequestPreflightChecksService.TimedCheck;
import org.olf.dcb.storage.EventLogRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

class PreflightChecksTimeoutTests {
	private SimpleMeterRegistry meterRegistry;
	private EventLogRepository eventLog;

	// The test instance is shared across tests (junit-platform.properties), so both are made per test
	@BeforeEach
	void freshRegistryAndEventLog() {
		meterRegistry = new SimpleMeterRegistry();
		eventLog = mock(EventLogRepository.class);
		when(eventLog.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void checksThatDoNotFinishInTimeRefuseTheRequest() {
		final var service = service(Duration.ofMillis(100), new HungCheck());

		final var refused = assertThrows(PreflightCheckFailedException.class,
			() -> service.check(command()).block(Duration.ofSeconds(5)));

		assertThat(refused.getFailedChecks().get(0).getCode(), is("PREFLIGHT_CHECKS_TIMED_OUT"));
	}

	@Test
	void aPlacementThatPassesIsTimedAsPassedAndEachCheckIsTimed() {
		service(Duration.ofSeconds(5), new QuickCheck()).check(command()).block(Duration.ofSeconds(5));

		assertThat(placementsTimed("passed"), is(1L));
		assertThat(meterRegistry.find(CHECK_DURATION_METRIC).tag("check", "QuickCheck").timer().count(), is(1L));
	}

	@Test
	void aRefusedPlacementIsTimedAsFailed() {
		final var service = service(Duration.ofSeconds(5), new RefusingCheck());

		assertThrows(PreflightCheckFailedException.class,
			() -> service.check(command()).block(Duration.ofSeconds(5)));

		assertThat(placementsTimed("failed"), is(1L));
	}

	@Test
	void aPlacementThatTimesOutIsTimedAsTimedOutAndOnlyFinishedChecksAreTimed() {
		final var service = service(Duration.ofMillis(100), new QuickCheck(), new HungCheck());

		assertThrows(PreflightCheckFailedException.class,
			() -> service.check(command()).block(Duration.ofSeconds(5)));

		assertThat(placementsTimed("timed_out"), is(1L));
		assertThat(meterRegistry.find(CHECK_DURATION_METRIC).tag("check", "QuickCheck").timer().count(), is(1L));
		assertThat(meterRegistry.find(CHECK_DURATION_METRIC).tag("check", "HungCheck").timer(), is(nullValue()));
	}

	@Test
	void theSummaryNamesTheBorrowingSystemAndTheSlowCheckButNotThePatron() {
		final var summary = PatronRequestPreflightChecksService.summary(command(), "timed_out", 45012,
			List.of(new TimedCheck("ResolvePatronPreflightCheck", 812, List.of(CheckResult.passed()))),
			"ResolvePatronRequestPreflightCheck",
			List.of(CheckResult.failedUm("PREFLIGHT_CHECKS_TIMED_OUT", "did not finish", "try again")));

		assertThat(summary, is("Preflight checks timed_out in 45012 ms: borrowingHostLms=SYSTEM"
			+ " copyChosen=false expedited=false codes=[PREFLIGHT_CHECKS_TIMED_OUT]"
			+ " checks=[ResolvePatronPreflightCheck=812ms, ResolvePatronRequestPreflightCheck=unfinished]"));
		assertThat(summary, not(containsString("patron-1")));
	}

	private long placementsTimed(String outcome) {
		final var timer = meterRegistry.find(DURATION_METRIC).tag("outcome", outcome).timer();

		return timer == null ? 0 : timer.count();
	}

	private PatronRequestPreflightChecksService service(Duration timeout, PreflightCheck... checks) {
		return new PatronRequestPreflightChecksService(List.of(checks), eventLog,
			new IntMessageService(), new PreflightChecksTimeout(timeout, null), meterRegistry);
	}

	private static PlacePatronRequestCommand command() {
		return PlacePatronRequestCommand.builder()
			.citation(PlacePatronRequestCommand.Citation.builder().bibClusterId(UUID.randomUUID()).build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder().code("pickup").build())
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localId("patron-1").localSystemCode("SYSTEM").agencyCode("agency").build())
			.build();
	}

	private static class QuickCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.just(List.of(CheckResult.passed()));
		}
	}

	private static class HungCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.never();
		}
	}

	private static class RefusingCheck implements PreflightCheck {
		@Override
		public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
			return Mono.just(List.of(CheckResult.failedUm("PATRON_BLOCKED", "blocked", "see staff")));
		}
	}
}
