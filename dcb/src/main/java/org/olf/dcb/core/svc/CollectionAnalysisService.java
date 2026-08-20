package org.olf.dcb.core.svc;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.olf.dcb.core.api.serde.ClusterSizeStat;
import org.olf.dcb.core.api.serde.CollectionOverlapStat;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.CollectionTotalsStat;
import org.olf.dcb.core.api.serde.SourceFormatStat;
import org.olf.dcb.storage.BibRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * The collection-analysis queries, and the concurrency limit and cache that make them safe to
 * expose on demand.
 *
 * <p>Why one permit for the whole group rather than one per endpoint, why a cache instead of a
 * scheduled rollup, and why a queued caller waits rather than being refused:
 * {@code docs/insights.md} part 5.
 */
@Slf4j
@Singleton
public class CollectionAnalysisService {

	/** Keys are Host LMS codes - a fixed vocabulary, so a caller cannot drive the cardinality. */
	private static final int MAX_CACHED_RESULTS = 1_000;

	private static final Duration WAIT_STEP = Duration.ofMillis(500);

	private final BibRepository bibRepository;
	private final Duration ttl;
	private final long waitAttempts;
	private final Duration waitStep;
	private final Semaphore permits;
	private final Cache<String, Object> results;

	/** Configurable because these are resource limits to tune against a real corpus. */
	@Inject
	public CollectionAnalysisService(BibRepository bibRepository,
		@Value("${dcb.insights.collection-analysis.cache-ttl:15m}") Duration ttl,
		@Value("${dcb.insights.collection-analysis.concurrency:1}") int concurrency,
		@Value("${dcb.insights.collection-analysis.max-wait:30s}") Duration maxWait) {

		this(bibRepository, ttl, concurrency, maxWait, WAIT_STEP);
	}

	/** Visible so the refusal path can be tested without waiting the configured maxWait. */
	CollectionAnalysisService(BibRepository bibRepository, Duration ttl, int concurrency,
		Duration maxWait, Duration waitStep) {

		this.bibRepository = bibRepository;
		this.ttl = ttl;
		this.waitStep = waitStep;
		this.waitAttempts = Math.max(1, maxWait.toMillis() / Math.max(1, waitStep.toMillis()));
		this.permits = new Semaphore(concurrency);

		this.results = Caffeine.newBuilder()
			.maximumSize(MAX_CACHED_RESULTS)
			.expireAfterWrite(ttl)
			.build();

		log.info("Collection analysis: {} concurrent, {} cache, {} max wait",
			concurrency, ttl, maxWait);
	}

	/** The consortium headline: distinct titles, singly held titles, holdings, sources. */
	public Mono<CollectionTotalsStat> totals() {
		return guarded("totals", () -> Mono.from(bibRepository.getCollectionTotals()));
	}

	/** Per source system: works contributed, and how many of those nobody else holds. */
	public Mono<List<CollectionProfileStat>> profile() {
		return guarded("profile",
			() -> Flux.from(bibRepository.getCollectionProfile()).collectList());
	}

	/** How many source systems hold each work - the honesty check on {@link #profile()}. */
	public Mono<List<ClusterSizeStat>> clusterSizeDistribution() {
		return guarded("cluster-size",
			() -> Flux.from(bibRepository.getClusterSizeDistribution()).collectList());
	}

	/** Format mix per source system, counted per work so it reconciles with the profile. */
	public Mono<List<SourceFormatStat>> formatProfile() {
		return guarded("format",
			() -> Flux.from(bibRepository.getFormatProfile()).collectList());
	}

	/**
	 * Who duplicates this library. One library against all others, never the full matrix - see
	 * BibRepository.getCollectionOverlapForLibrary.
	 *
	 * @param libraryCode comma-separated Host LMS codes from StatsScopeGuard, never straight
	 *   from the query string.
	 */
	public Mono<List<CollectionOverlapStat>> overlapFor(String libraryCode) {
		return guarded("overlap:" + libraryCode,
			() -> Flux.from(bibRepository.getCollectionOverlapForLibrary(libraryCode))
				.collectList());
	}

	/** Serve from cache, or take the one permit and compute. */
	private <T> Mono<T> guarded(String key, Supplier<Mono<T>> work) {
		return Mono.defer(() -> {
			final T hit = cached(key);

			if (hit != null) {
				return Mono.just(hit);
			}

			return compute(key, work);
		});
	}

	private <T> Mono<T> compute(String key, Supplier<Mono<T>> work) {
		return Mono.defer(() -> {
				if (!permits.tryAcquire()) {
					return Mono.error(new Busy());
				}

				// Whoever held the permit may have just answered this very question.
				final T hit = cached(key);

				if (hit != null) {
					permits.release();
					return Mono.just(hit);
				}

				// INFO, not DEBUG: the only place the real cost of a catalogue aggregate
				// becomes visible, and the cache keeps it to a handful of lines an hour.
				final var startedAt = System.nanoTime();

				return work.get()
					.doOnNext(value -> results.put(key, value))
					.doOnSuccess(value -> log.info("Collection analysis [{}] took {}ms", key,
						(System.nanoTime() - startedAt) / 1_000_000))
					.doFinally(signal -> permits.release());
			})
			.retryWhen(Retry.fixedDelay(waitAttempts, waitStep)
				.filter(Busy.class::isInstance)
				.onRetryExhaustedThrow((spec, signal) -> tooBusy(key)));
	}

	// One cache, not five typed ones: keys are disjoint by construction (each accessor owns its
	// literal), so nothing can read a value back as the wrong type.
	@SuppressWarnings("unchecked")
	private <T> T cached(String key) {
		return (T) results.getIfPresent(key);
	}

	private HttpStatusException tooBusy(String key) {
		log.warn("Refusing collection analysis [{}]: still busy after {} attempts at {}",
			key, waitAttempts, waitStep);

		return new HttpStatusException(HttpStatus.TOO_MANY_REQUESTS,
			"Collection analysis is busy. These figures are cached for "
				+ ttl.toMinutes() + " minutes once computed - try again shortly.");
	}

	/** Internal only: never leaves this class, so it carries no message or stack trace cost. */
	private static final class Busy extends RuntimeException {
		private Busy() {
			super(null, null, false, false);
		}
	}
}
