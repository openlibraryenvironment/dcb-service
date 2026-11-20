package org.olf.dcb.core.svc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.olf.dcb.core.api.serde.LendingAnalysis;
import org.olf.dcb.core.api.serde.LendingFormatStat;
import org.olf.dcb.core.api.serde.LentTitleStat;
import org.olf.dcb.storage.PatronRequestRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What other libraries ask a library to supply, by title and by format. Over a year at
 * most; a group below {@link #MINIMUM_GROUP} requests is counted as other, never shown on its
 * own. Cached per library and window, as {@link CollectionAnalysisService} is.
 */
@Singleton
public class LendingAnalysisService {
	/** The fewest requests a group may show on its own. */
	public static final int MINIMUM_GROUP = 5;
	/** The most titles listed; the rest are other. */
	public static final int MAX_TITLES = 50;
	/** The longest window, whatever a caller asks for. */
	public static final Duration MAX_WINDOW = Duration.ofDays(365);

	private static final int MAX_CACHED = 1_000;

	private final PatronRequestRepository requests;
	private final Clock clock;
	private final Duration ttl;
	private final Cache<String, Mono<LendingAnalysis<LentTitleStat>>> titles;
	private final Cache<String, Mono<LendingAnalysis<LendingFormatStat>>> formats;

	@Inject
	public LendingAnalysisService(PatronRequestRepository requests,
		@Value("${dcb.insights.collection-analysis.cache-ttl}") Duration ttl) {

		this(requests, ttl, Clock.systemUTC());
	}

	LendingAnalysisService(PatronRequestRepository requests, Duration ttl, Clock clock) {
		this.requests = requests;
		this.clock = clock;
		this.ttl = ttl;
		// Keyed by Host LMS codes, agencies and window: one entry per scope and date range asked for
		this.titles = Caffeine.newBuilder().maximumSize(MAX_CACHED).expireAfterWrite(ttl).build();
		this.formats = Caffeine.newBuilder().maximumSize(MAX_CACHED).expireAfterWrite(ttl).build();
	}

	public Mono<LendingAnalysis<LentTitleStat>> mostLent(String libraryCode, @Nullable String agencyCodes,
		@Nullable Instant startDate, @Nullable Instant endDate) {

		final var window = Window.of(startDate, endDate, clock.instant());

		return titles.get(window.key(libraryCode + "|" + agencyCodes), key -> analyse(libraryCode, agencyCodes, window,
			Flux.from(requests.findMostLentTitles(libraryCode, agencyCodes, window.start(), window.end(), MINIMUM_GROUP,
				MAX_TITLES)),
			LentTitleStat::requestCount));
	}

	public Mono<LendingAnalysis<LendingFormatStat>> byFormat(String libraryCode, @Nullable String agencyCodes,
		@Nullable Instant startDate, @Nullable Instant endDate) {

		final var window = Window.of(startDate, endDate, clock.instant());

		return formats.get(window.key(libraryCode + "|" + agencyCodes), key -> analyse(libraryCode, agencyCodes, window,
			Flux.from(requests.findLendingByFormat(libraryCode, agencyCodes, window.start(), window.end(), MINIMUM_GROUP)),
			LendingFormatStat::requestCount));
	}

	private <T> Mono<LendingAnalysis<T>> analyse(String libraryCode, @Nullable String agencyCodes, Window window,
		Flux<T> groups, Function<T, Long> countOf) {

		final Mono<Long> total = Mono.from(requests.countRequestsSuppliedBy(libraryCode, agencyCodes, window.start(),
				window.end()))
			.defaultIfEmpty(0L);

		// As CollectionAnalysisService shares its queries: callers share one read, a failure is not kept
		return Mono.zip(groups.collectList(), total)
			.map(both -> {
				final List<T> shown = both.getT1();
				final long inGroups = shown.stream().mapToLong(countOf::apply).sum();
				return new LendingAnalysis<>(shown, Math.max(0, both.getT2() - inGroups), both.getT2());
			})
			.cache(value -> ttl, error -> Duration.ZERO, () -> Duration.ZERO);
	}

	/** The window asked for, ending now at the latest and starting at most a year before its end. */
	record Window(Instant start, Instant end) {
		static Window of(@Nullable Instant startDate, @Nullable Instant endDate, Instant now) {
			final var end = endDate == null || endDate.isAfter(now) ? now : endDate;
			final var earliest = end.minus(MAX_WINDOW);
			final var start = startDate == null || startDate.isBefore(earliest) ? earliest : startDate;
			return new Window(start, end);
		}

		String key(String libraryCode) {
			// To the hour: a window ending "now" would otherwise be a new key on every read
			return "%s|%d|%d".formatted(libraryCode, start.getEpochSecond() / 3600, end.getEpochSecond() / 3600);
		}
	}
}
