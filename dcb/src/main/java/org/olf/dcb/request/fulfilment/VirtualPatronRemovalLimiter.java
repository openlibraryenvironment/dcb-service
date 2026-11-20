package org.olf.dcb.request.fulfilment;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Virtual patron removals per member of staff, counted in one-minute windows that open at the
 * first call, because every removal reads and changes a record in a system we do not own.
 */
@Singleton
public class VirtualPatronRemovalLimiter {
	static final Duration WINDOW = Duration.ofMinutes(1);

	// Not a setting: no deployment needs to change it, and a bad value would stop its controller being created
	static final int PER_CALLER = 10;

	private final int perCaller;

	// Keyed by the token's subject: staff accounts, bounded by the cap and the window
	private final Cache<String, AtomicInteger> callers = Caffeine.newBuilder()
		.maximumSize(10_000)
		.expireAfterWrite(WINDOW)
		.build();

	@Inject
	public VirtualPatronRemovalLimiter() {
		this(PER_CALLER);
	}

	VirtualPatronRemovalLimiter(int perCaller) {
		this.perCaller = perCaller;
	}

	public boolean tryAcquireForCaller(String caller) {
		return callers.get(caller, key -> new AtomicInteger()).incrementAndGet() <= perCaller;
	}
}
