package org.olf.dcb.core.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.WeeklyMeasureRepository;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;

/**
 * Records last week's stored measures for every library, once a week, from DCB's own tables only:
 * hundreds of libraries, two queries each, two at a time. Rewriting a week replaces it, so a second
 * instance running at the same moment does no harm.
 */
@Slf4j
@Singleton
public class WeeklyMeasureJob {
	static final int KEEP_WEEKS = 52;
	private static final int CONCURRENCY = 2;

	private final AgencyRepository agencies;
	private final WeeklyMeasures measures;
	private final WeeklyMeasureRepository repository;
	private final Clock clock;

	public WeeklyMeasureJob(AgencyRepository agencies, WeeklyMeasures measures, WeeklyMeasureRepository repository) {
		this(agencies, measures, repository, Clock.systemUTC());
	}

	WeeklyMeasureJob(AgencyRepository agencies, WeeklyMeasures measures, WeeklyMeasureRepository repository,
		Clock clock) {

		this.agencies = agencies;
		this.measures = measures;
		this.repository = repository;
		this.clock = clock;
	}

	@AppTask
	@Scheduled(cron = "0 30 1 * * MON")
	public void scheduledRecord() {
		recordLastWeek()
			.doOnSuccess(recorded -> log.info("Weekly measures: recorded {} figure(s)", recorded))
			.doOnError(error -> log.error("Weekly measures failed", error))
			.subscribe();
	}

	/** @return how many figures were recorded */
	public Mono<Long> recordLastWeek() {
		final Instant now = clock.instant();
		final Instant thisWeek = StaffNotificationService.startOfWeek(now);
		final Instant lastWeek = thisWeek.minus(Duration.ofDays(7));
		final LocalDate lastWeekStart = LocalDate.ofInstant(lastWeek, ZoneOffset.UTC);
		final var stored = Arrays.stream(TrendMeasure.values())
			.filter(measure -> measure.source() == TrendMeasure.Source.STORED)
			.toList();

		return Flux.from(agencies.queryAll())
			.flatMap(agency -> Flux.fromIterable(stored)
				.concatMap(measure -> measures.figure(agency.getCode(), measure, lastWeek, thisWeek)
					.flatMap(figure -> Mono.from(repository.record(agency.getCode(), measure.name(), lastWeekStart,
						figure.value(), (int) Math.min(Integer.MAX_VALUE, figure.samples()), now)))),
				CONCURRENCY)
			.count()
			.flatMap(recorded -> Mono.from(repository.deleteBefore(lastWeekStart.minusWeeks(KEEP_WEEKS)))
				.thenReturn(recorded));
	}
}
