package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.storage.AlarmRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
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

	private void raiseFor(String locationCode) {
		singleValueFrom(alarmsService.raiseAccumulating(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(ALARM_CODE))
				.code(ALARM_CODE)
				.expires(Instant.now().plus(Duration.ofDays(5)))
				.build(),
			"unmappedLocationCodes", locationCode));
	}

	private List<Alarm> allAlarms() {
		return reactor.core.publisher.Flux.from(alarmRepository.queryAll())
			.collectList()
			.block();
	}
}
