package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.configuration.NotificationEndpointDefinition;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.storage.AlarmRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 * Alarms that stand for a set of related occurrences.
 * <p>
 * Some conditions are naturally one-per-thing - an unmapped location - and an
 * alarm per thing means a webhook post per thing. Onboarding a shared system with
 * sixty branches is exactly when that becomes unbearable.
 */
@DcbTest
class AlarmsServiceTests {
	private static final String ALARM_CODE = "ILS.EXAMPLE.LOCATION_TO_AGENCY_FAILURE.Location";

	private final DataAccess dataAccess = new DataAccess();

	@Inject
	AlarmsService alarmsService;
	@Inject
	AlarmRepository alarmRepository;

	@BeforeEach
	void beforeEach() {
		dataAccess.deleteAll(alarmRepository.queryAll(),
			alarm -> alarmRepository.delete(alarm.getId()));
	}

	@Test
	void shouldGatherDistinctValuesUnderOneAlarm() {
		raiseFor("STACKS");
		raiseFor("REF");
		raiseFor("JUV");

		final var alarms = allAlarms();

		assertThat("Every unmapped location belongs to one alarm", alarms, hasSize(1));

		assertThat(unmappedCodesOf(alarms.get(0)), contains("JUV", "REF", "STACKS"));
	}

	@Test
	void shouldNotRepeatAValueItHasAlreadySeen() {
		raiseFor("STACKS");
		raiseFor("STACKS");

		final var alarm = allAlarms().get(0);

		assertThat(unmappedCodesOf(alarm), contains("STACKS"));

		// Still counted, so the volume behind the alarm is not lost
		assertThat(alarm.getRepeatCount(), is(1L));
	}

	private List<String> unmappedCodesOf(Alarm alarm) {
		final var codes = (Collection<?>) alarm.getAlarmDetails().get("unmappedLocationCodes");

		return codes.stream().map(String::valueOf).toList();
	}

	@Test
	void shouldNotLoseValuesReportedConcurrently() {
		// The condition is reported one location at a time from the per-item
		// availability path, so every reporter arrives at once. A read-modify-write
		// in Java keeps only the last writer's set; this is the case that proves the
		// accumulation happens in the database.
		final var locationCodes = IntStream.range(0, 60)
			.mapToObj("BRANCH-%02d"::formatted)
			.toList();

		Flux.fromIterable(locationCodes)
			.flatMap(this::raiseAccumulating, locationCodes.size())
			.then()
			.block();

		final var alarms = allAlarms();

		assertThat("Concurrent reporters share one alarm", alarms, hasSize(1));

		assertThat("Every concurrently reported location survives",
			unmappedCodesOf(alarms.get(0)), containsInAnyOrder(locationCodes.toArray()));
	}

	private void raiseFor(String locationCode) {
		raiseAccumulating(locationCode).block();
	}

	private Mono<Void> raiseAccumulating(String locationCode) {
		return alarmsService.raiseAccumulating(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(ALARM_CODE))
				.code(ALARM_CODE)
				.expires(Instant.now().plus(Duration.ofDays(5)))
				.build(),
			"unmappedLocationCodes", locationCode);
	}

	private List<Alarm> allAlarms() {
		return Flux.from(alarmRepository.queryAll())
			.collectList()
			.block();
	}

	@Test
	void shouldPostToTheWebhookWhenAnAlarmIsRaised() {
		final var posted = new ArrayList<String>();
		final var notifying = notifyingService(posted);

		notifying.raise(pingFailure()).block();

		assertThat(posted, hasSize(1));
		assertThat(posted.get(0), containsString(PING_FAILURE + " ACTIVATED"));
	}

	@Test
	void shouldPostToTheWebhookWhenAnAlarmThatExistedIsCancelled() {
		final var posted = new ArrayList<String>();
		final var notifying = notifyingService(posted);

		notifying.raise(pingFailure()).block();
		notifying.cancel(PING_FAILURE).block();

		assertThat(posted, hasSize(2));
		assertThat(posted.get(1), containsString(PING_FAILURE + " DEACTIVATED"));
	}

	@Test
	void shouldNotPostWhenCancellingAnAlarmThatDoesNotExist() {
		final var posted = new ArrayList<String>();

		// What every passing ping does for a healthy host, every day
		notifyingService(posted).cancel(PING_FAILURE).block();

		assertThat(posted, is(empty()));
	}

	private static final String PING_FAILURE = "ILS.EXAMPLE.PING_FAILURE";

	private Alarm pingFailure() {
		return Alarm.builder()
			.id(UUIDUtils.generateAlarmId(PING_FAILURE))
			.code(PING_FAILURE)
			.build();
	}

	// The webhook is recorded rather than called; whether a post is made at all is under test
	private AlarmsService notifyingService(List<String> posted) {
		final var webhook = new NotificationEndpointDefinition("test-webhook");
		webhook.setUrl("https://hooks.example.com/alarms");
		webhook.setProfile("SLACK");

		final var service = spy(new AlarmsService(alarmRepository, List.of(webhook)));

		doAnswer(invocation -> Mono.fromRunnable(() -> posted.add(invocation.getArgument(1).toString())))
			.when(service).publishToWebhook(anyString(), anyMap());

		return service;
	}
}
