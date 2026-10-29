package org.olf.dcb.core.notifications;

import static services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder.QUERY_PATHS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.security.RequestInvolvement;
import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.WeeklyMeasureRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestAuditRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A library's notifications (S-20), worked out when read from what DCB already holds: the Home
 * counts that need staff, notes other libraries added to its requests, and its weekly trends
 * (S-23). Nothing is stored per member of staff. docs/operational/staff-notifications.md.
 */
@Slf4j
@Singleton
public class StaffNotificationService {
	/** The most a feed holds, newest first. */
	public static final int MAX_ITEMS = 50;

	static final Duration FEED_TTL = Duration.ofMinutes(1);
	static final Duration TREND_TTL = Duration.ofHours(12);
	static final Duration NOTE_WINDOW = Duration.ofDays(7);
	static final Duration EXCEPTION_WINDOW = Duration.ofDays(1);
	static final int TRAILING_WEEKS = 8;
	/** A stored measure says nothing until this many of its trailing weeks are recorded. */
	static final int MIN_STORED_WEEKS = 4;
	private static final int MAX_NOTED_REQUESTS = 20;
	// Count queries against tables DCB owns; two at a time per library being read
	private static final int QUERY_CONCURRENCY = 2;

	private final PostgresPatronRequestRepository requests;
	private final PostgresPatronRequestAuditRepository audits;
	private final PatronRequestNoteRepository notes;
	private final WeeklyMeasures weeklyMeasures;
	private final WeeklyMeasureRepository storedMeasures;
	private final Clock clock;

	// Keyed by agency code from DCB's own records: hundreds per consortium, a minute each
	private final Cache<String, List<StaffNotification>> feeds = Caffeine.newBuilder()
		.maximumSize(1_000)
		.expireAfterWrite(FEED_TTL)
		.build();

	// Keyed by agency code; a week's trend is worked out at most twice a day per library read
	private final Cache<String, List<StaffNotification>> trends = Caffeine.newBuilder()
		.maximumSize(1_000)
		.expireAfterWrite(TREND_TTL)
		.build();

	@Inject
	public StaffNotificationService(PostgresPatronRequestRepository requests,
		PostgresPatronRequestAuditRepository audits, PatronRequestNoteRepository notes,
		WeeklyMeasures weeklyMeasures, WeeklyMeasureRepository storedMeasures) {

		this(requests, audits, notes, weeklyMeasures, storedMeasures, Clock.systemUTC());
	}

	StaffNotificationService(PostgresPatronRequestRepository requests,
		PostgresPatronRequestAuditRepository audits, PatronRequestNoteRepository notes,
		WeeklyMeasures weeklyMeasures, WeeklyMeasureRepository storedMeasures, Clock clock) {

		this.requests = requests;
		this.audits = audits;
		this.notes = notes;
		this.weeklyMeasures = weeklyMeasures;
		this.storedMeasures = storedMeasures;
		this.clock = clock;
	}

	/** Every item for one library, newest first, at most {@link #MAX_ITEMS}. */
	public Mono<List<StaffNotification>> feedFor(String agencyCode) {
		final var cached = feeds.getIfPresent(agencyCode);
		if (cached != null) {
			return Mono.just(cached);
		}

		return Flux.concat(counts(agencyCode), notesFor(agencyCode),
				trendsFor(agencyCode).flatMapIterable(found -> found))
			.sort(Comparator.comparing(StaffNotification::occurredAt, Comparator.reverseOrder()))
			.take(MAX_ITEMS)
			.collectList()
			.doOnNext(items -> feeds.put(agencyCode, items));
	}

	/** This library's weekly trend alerts on their own: what the TREND items in the feed are. */
	public Mono<List<StaffNotification>> trendsFor(String agencyCode) {
		final var cached = trends.getIfPresent(agencyCode);
		if (cached != null) {
			return Mono.just(cached);
		}

		final var thisWeek = startOfWeek(clock.instant());

		return Flux.fromArray(TrendMeasure.values())
			.concatMap(measure -> measure.source() == TrendMeasure.Source.AUDIT
				? trend(agencyCode, measure, thisWeek)
				: storedTrend(agencyCode, measure, thisWeek))
			.collectList()
			.doOnNext(found -> trends.put(agencyCode, found));
	}

	private Flux<StaffNotification> counts(String agencyCode) {
		final var now = clock.instant();
		final var since = now.minus(EXCEPTION_WINDOW);
		final var day = LocalDate.ofInstant(now, ZoneOffset.UTC).toString();

		return Flux.fromArray(HomeBucket.values())
			.flatMap(bucket -> count(bucket, agencyCode, since)
				.filter(count -> count > 0)
				.map(count -> StaffNotification.count(bucket, count, day,
					(bucket.windowed() ? since : now).toString())), QUERY_CONCURRENCY);
	}

	private Mono<Long> count(HomeBucket bucket, String agencyCode, Instant since) {
		final QuerySpecification<PatronRequest> scope = scoped(bucket.scope(), agencyCode);
		final var statuses = bucket.statuses();

		final QuerySpecification<PatronRequest> spec = (root, query, cb) -> {
			final var inStatus = root.get("status").in(statuses);
			final var inScope = scope.toPredicate(root, query, cb);
			return bucket.windowed()
				? cb.and(inScope, inStatus, cb.greaterThanOrEqualTo(root.get("dateUpdated"), since))
				: cb.and(inScope, inStatus);
		};

		return Mono.from(requests.count(spec));
	}

	private static QuerySpecification<PatronRequest> scoped(HomeBucket.Scope scope, String agencyCode) {
		return scope == HomeBucket.Scope.BORROWED
			? QUERY_PATHS.get("patronAgencyCode").isAnyOf(List.of(agencyCode))
			: QUERY_PATHS.get("supplyingAgencyCode").isAnyOf(List.of(agencyCode));
	}

	/** One item per request another library or the consortium noted this week, for the newest such note. */
	private Flux<StaffNotification> notesFor(String agencyCode) {
		final var since = clock.instant().minus(NOTE_WINDOW);
		final QuerySpecification<PatronRequest> involved = RequestInvolvement.involving(List.of(agencyCode));

		final QuerySpecification<PatronRequest> noted = (root, query, cb) -> {
			final Subquery<UUID> noteRequests = query.subquery(UUID.class);
			final Root<PatronRequestNote> note = noteRequests.from(PatronRequestNote.class);
			noteRequests.select(note.get("patronRequestId")).where(
				cb.greaterThanOrEqualTo(note.get("dateCreated"), since),
				cb.isNull(note.get("retractedAt")),
				cb.or(cb.isNull(note.get("agencyCode")), cb.notEqual(note.get("agencyCode"), agencyCode)));

			return cb.and(involved.toPredicate(root, query, cb), root.get("id").in(noteRequests));
		};

		return Mono.from(requests.findAll(noted, Pageable.from(0, MAX_NOTED_REQUESTS)))
			.flatMapMany(page -> Flux.fromIterable(page.getContent()))
			.flatMap(request -> newestOtherNote(request.getId(), agencyCode, since), QUERY_CONCURRENCY);
	}

	private Mono<StaffNotification> newestOtherNote(UUID patronRequestId, String agencyCode, Instant since) {
		return Mono.from(notes.findByPatronRequestIdOrderByDateCreatedDesc(patronRequestId, Pageable.from(0, 10)))
			.flatMapMany(page -> Flux.fromIterable(page.getContent()))
			.filter(note -> note.getRetractedAt() == null
				&& !note.getDateCreated().isBefore(since)
				&& !Objects.equals(note.getAgencyCode(), agencyCode))
			.next()
			.map(note -> StaffNotification.note(patronRequestId.toString(), note.getId().toString(),
				note.getDateCreated().toString(), note.getAgencyCode()));
	}

	private Mono<StaffNotification> trend(String agencyCode, TrendMeasure measure, Instant thisWeek) {
		return Flux.range(0, TRAILING_WEEKS + 1)
			.flatMapSequential(weeksBack -> transitions(agencyCode, measure,
				thisWeek.minus(Duration.ofDays(7L * weeksBack)),
				weeksBack == 0 ? clock.instant() : thisWeek.minus(Duration.ofDays(7L * (weeksBack - 1)))),
				QUERY_CONCURRENCY)
			.collectList()
			.flatMap(counts -> {
				final long current = counts.getFirst();
				final double trailing = counts.subList(1, counts.size()).stream()
					.mapToLong(Long::longValue).average().orElse(0);
				return measure.alerts(current, trailing)
					? Mono.just(StaffNotification.trend(measure, weekName(thisWeek), thisWeek.toString(),
						current, trailing))
					: Mono.empty();
			});
	}

	/** This week so far against the recorded trailing weeks; silent until enough are recorded. */
	private Mono<StaffNotification> storedTrend(String agencyCode, TrendMeasure measure, Instant thisWeek) {
		final var week = LocalDate.ofInstant(thisWeek, ZoneOffset.UTC);

		return Flux.from(storedMeasures.trailing(agencyCode, measure.name(), week.minusWeeks(TRAILING_WEEKS), week))
			.collectList()
			.filter(recorded -> recorded.size() >= MIN_STORED_WEEKS)
			.flatMap(recorded -> weeklyMeasures.figure(agencyCode, measure, thisWeek, clock.instant())
				.flatMap(current -> {
					final double trailing = recorded.stream().mapToDouble(WeeklyMeasureRepository.Stored::value)
						.average().orElse(0);
					return measure.alerts(current.value(), trailing)
						? Mono.just(StaffNotification.trend(measure, weekName(thisWeek), thisWeek.toString(),
							Math.round(current.value()), trailing))
						: Mono.empty();
				}));
	}

	/** Requests of this library that moved into the measure's status between from and to. */
	private Mono<Long> transitions(String agencyCode, TrendMeasure measure, Instant from, Instant to) {
		final var path = measure.scope() == HomeBucket.Scope.BORROWED
			? QUERY_PATHS.get("patronAgencyCode")
			: QUERY_PATHS.get("supplyingAgencyCode");
		final QuerySpecification<PatronRequestAudit> ofLibrary = path.under("patronRequest").isAnyOf(List.of(agencyCode));

		final QuerySpecification<PatronRequestAudit> spec = (root, query, cb) -> cb.and(
			ofLibrary.toPredicate(root, query, cb),
			cb.equal(root.get("toStatus"), measure.toStatus()),
			cb.greaterThanOrEqualTo(root.get("auditDate"), from),
			cb.lessThan(root.get("auditDate"), to));

		return Mono.from(audits.count(spec));
	}

	static Instant startOfWeek(Instant now) {
		final var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		return today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
	}

	private static String weekName(Instant weekStart) {
		final var day = LocalDate.ofInstant(weekStart, ZoneOffset.UTC);
		return "%d-W%02d".formatted(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}
}
