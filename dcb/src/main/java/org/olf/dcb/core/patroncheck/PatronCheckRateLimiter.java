package org.olf.dcb.core.patroncheck;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/**
 * Patron checks counted in one-minute windows that open at the first call: per member of staff,
 * and per library system, because every check is a live read of a system we do not own.
 */
@Singleton
public class PatronCheckRateLimiter {
	static final Duration WINDOW = Duration.ofMinutes(1);

	private final int perCaller;
	private final int perHostLms;

	// Keyed by the token's subject: staff accounts, bounded by the cap and the window
	private final Cache<String, AtomicInteger> callers = Caffeine.newBuilder()
		.maximumSize(10_000)
		.expireAfterWrite(WINDOW)
		.build();

	// Keyed by Host LMS code, from DCB's own records: tens per consortium
	private final Cache<String, AtomicInteger> hostLmss = Caffeine.newBuilder()
		.maximumSize(1_000)
		.expireAfterWrite(WINDOW)
		.build();

	public PatronCheckRateLimiter(
		@Value("${dcb.patron-checks.per-caller-per-minute}") int perCaller,
		@Value("${dcb.patron-checks.per-host-lms-per-minute}") int perHostLms) {

		this.perCaller = perCaller;
		this.perHostLms = perHostLms;
	}

	public boolean tryAcquireForCaller(String caller) {
		return callers.get(caller, key -> new AtomicInteger()).incrementAndGet() <= perCaller;
	}

	public boolean tryAcquireForHostLms(String hostLmsCode) {
		return hostLmss.get(hostLmsCode, key -> new AtomicInteger()).incrementAndGet() <= perHostLms;
	}

	public static long retryAfterSeconds() {
		return WINDOW.toSeconds();
	}
}
