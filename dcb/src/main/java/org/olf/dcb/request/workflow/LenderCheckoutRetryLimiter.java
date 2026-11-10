package org.olf.dcb.request.workflow;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/**
 * Lender checkout retries per member of staff, counted in one-minute windows that open at the
 * first call, because every retry reads and checks out a copy in a system we do not own.
 */
@Singleton
public class LenderCheckoutRetryLimiter {
	static final Duration WINDOW = Duration.ofMinutes(1);

	private final int perCaller;

	// Keyed by the token's subject: staff accounts, bounded by the cap and the window
	private final Cache<String, AtomicInteger> callers = Caffeine.newBuilder()
		.maximumSize(10_000)
		.expireAfterWrite(WINDOW)
		.build();

	public LenderCheckoutRetryLimiter(
		@Value("${dcb.requests.lender-checkout-retry.per-caller-per-minute}") int perCaller) {

		this.perCaller = perCaller;
	}

	public boolean tryAcquireForCaller(String caller) {
		return callers.get(caller, key -> new AtomicInteger()).incrementAndGet() <= perCaller;
	}
}
