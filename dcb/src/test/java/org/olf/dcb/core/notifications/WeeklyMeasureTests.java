package org.olf.dcb.core.notifications;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.WeeklyMeasureRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/** The stored measures: recorded each week, and a trend once four weeks are recorded. */
@DcbTest
@TestInstance(PER_CLASS)
class WeeklyMeasureTests {
	private static final String OWN = "WEEKLY_OWN";
	private static final String SPARSE = "WEEKLY_SPARSE";

	@Inject
	private WeeklyMeasures measures;
	@Inject
	private WeeklyMeasureRepository repository;
	@Inject
	private AgencyRepository agencies;
	@Inject
	private StaffNotificationService notifications;
	@Inject
	private PatronRequestAuditRepository audits;
	@Inject
	private R2dbcOperations r2dbcOperations;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private final Instant thisWeek = StaffNotificationService.startOfWeek(Instant.now());

	@BeforeAll
	void beforeAll() {
		deleteAll();
		final var hostLms = hostLmsFixture.createDummyHostLms("WEEKLY_LMS");
		final var own = agencyFixture.defineAgency(OWN, "Own Library", hostLms);
		final var sparse = agencyFixture.defineAgency(SPARSE, "Sparse Library", hostLms);

		for (int index = 0; index < 6; index++) {
			request(hostLms, own, "own-" + index);
			request(hostLms, sparse, "sparse-" + index);
		}
		final var collected = request(hostLms, own, "own-turnaround");
		singleValueFrom(audits.save(PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(collected)
			.briefDescription("weekly-ready")
			.fromStatus(PatronRequest.Status.RECEIVED_AT_PICKUP)
			.toStatus(PatronRequest.Status.READY_FOR_PICKUP)
			.build()));
		// Ready on Tuesday of last week, placed a hundred hours before
		final var ready = thisWeek.minus(Duration.ofDays(6));
		execute("UPDATE patron_request_audit SET audit_date = '%s' WHERE brief_description = 'weekly-ready'"
			.formatted(ready));
		execute("UPDATE patron_request SET date_created = '%s' WHERE id = '%s'"
			.formatted(ready.minus(Duration.ofHours(100)), collected.getId()));
	}

	@AfterAll
	void deleteAll() {
		execute("DELETE FROM library_weekly_measure WHERE agency_code IN ('%s', '%s')".formatted(OWN, SPARSE));
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void theJobRecordsLastWeeksTurnaroundAndWhatIsWaitingToBeCollected() {
		final var job = new WeeklyMeasureJob(agencies, measures, repository,
			Clock.fixed(thisWeek.plus(Duration.ofHours(2)), ZoneOffset.UTC));
		singleValueFrom(job.recordLastWeek());

		final var lastWeek = LocalDate.ofInstant(thisWeek, ZoneOffset.UTC).minusWeeks(1);
		assertThat(stored(OWN, TrendMeasure.AWAITING_PICKUP, lastWeek).getFirst().value(), is(7.0));
		assertThat(stored(OWN, TrendMeasure.MEDIAN_TURNAROUND_HOURS, lastWeek).getFirst().value(), closeTo(100.0, 0.01));
		assertThat("Nothing reached a sparse library's hold shelf last week, so there is no median",
			stored(SPARSE, TrendMeasure.MEDIAN_TURNAROUND_HOURS, lastWeek), is(empty()));
	}

	@Test
	void aStoredMeasureAlertsOnlyOnceFourWeeksAreRecorded() {
		final var week = LocalDate.ofInstant(thisWeek, ZoneOffset.UTC);
		for (int back = 1; back <= 4; back++) {
			singleValueFrom(repository.record(OWN, TrendMeasure.AWAITING_PICKUP.name(), week.minusWeeks(back), 2.0, 2, Instant.now()));
		}
		for (int back = 1; back <= 3; back++) {
			singleValueFrom(repository.record(SPARSE, TrendMeasure.AWAITING_PICKUP.name(), week.minusWeeks(back), 1.0, 1, Instant.now()));
		}

		final var own = singleValueFrom(notifications.trendsFor(OWN)).stream()
			.filter(item -> TrendMeasure.AWAITING_PICKUP.name().equals(item.measure())).toList();
		assertThat(own, hasSize(1));
		assertThat(own.getFirst().current(), is(7.0));

		assertThat(singleValueFrom(notifications.trendsFor(SPARSE)).stream()
			.filter(item -> TrendMeasure.AWAITING_PICKUP.name().equals(item.measure())).toList(), is(empty()));
	}

	private List<WeeklyMeasureRepository.Stored> stored(String agency, TrendMeasure measure, LocalDate week) {
		return Flux.from(repository.trailing(agency, measure.name(), week, week.plusDays(1))).collectList().block();
	}

	private PatronRequest request(DataHostLms hostLms, DataAgency agency, String patronHome) {
		final var patron = patronFixture.savePatron(patronHome);
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, patronHome + "-id",
			true, "-", patronHome, agency);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(PatronRequest.Status.READY_FOR_PICKUP)
			.dateUpdated(Instant.now())
			.build());
	}

	private void execute(String sql) {
		Flux.from(r2dbcOperations.withConnection(connection ->
				Flux.from(connection.createStatement(sql).execute()).flatMap(result -> result.getRowsUpdated())))
			.collectList()
			.block(Duration.ofMinutes(1));
	}
}
