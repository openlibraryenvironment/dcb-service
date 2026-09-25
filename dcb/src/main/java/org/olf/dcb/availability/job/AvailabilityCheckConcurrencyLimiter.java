package org.olf.dcb.availability.job;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Limits remote and database work for one availability-job chunk without blocking a reactor thread. */
final class AvailabilityCheckConcurrencyLimiter {
	private final PermitPool remoteInstanceWide;
	private final int remotePerSourceLimit;
	private final PermitPool mappingWrites;
	private final Map<UUID, PermitPool> remoteBySource = new ConcurrentHashMap<>();

	AvailabilityCheckConcurrencyLimiter(AvailabilityCheckJobConfig.Concurrency config) {
		this(instanceWideLimit(config.getInstanceWide()), config.getPerSource(), config.getMappingWrites());
	}

	AvailabilityCheckConcurrencyLimiter(int remoteInstanceWide, int remotePerSource,
		int mappingWriteConcurrency) {

		this.remoteInstanceWide = new PermitPool(remoteInstanceWide, "remote instance-wide");
		this.remotePerSourceLimit = requirePositive(remotePerSource, "remote per-source");
		this.mappingWrites = new PermitPool(mappingWriteConcurrency, "mapping writes");
	}

	<T> Mono<T> withRemoteLimit(UUID sourceSystemId, Supplier<Mono<T>> work) {
		final var sourcePermits = remoteBySource.computeIfAbsent(sourceSystemId,
			ignored -> new PermitPool(remotePerSourceLimit, "remote source " + sourceSystemId));

		return sourcePermits.withPermit(() -> remoteInstanceWide.withPermit(work));
	}

	<T> Mono<T> withMappingWriteLimit(Supplier<Mono<T>> work) {
		return mappingWrites.withPermit(work);
	}

	private static int instanceWideLimit(Optional<Integer> configured) {
		return configured.orElseGet(() -> Math.max(Runtime.getRuntime().availableProcessors() / 4, 5));
	}

	private static int requirePositive(int limit, String name) {
		if (limit < 1) {
			throw new IllegalArgumentException("Availability concurrency %s must be at least one".formatted(name));
		}
		return limit;
	}

	private static final class PermitPool {
		private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
		private int available;

		private PermitPool(int limit, String name) {
			this.available = requirePositive(limit, name);
		}

		private <T> Mono<T> withPermit(Supplier<Mono<T>> work) {
			return acquire().flatMap(permit -> Mono.defer(work)
				.doFinally(ignored -> permit.release()));
		}

		private Mono<Permit> acquire() {
			return Mono.defer(() -> {
				final Waiter waiter;
				synchronized (this) {
					if (available > 0) {
						available--;
						return Mono.just(new Permit(this));
					}
					waiter = new Waiter();
					waiters.addLast(waiter);
				}
				return waiter.sink.asMono().doOnCancel(() -> {
					waiter.cancelled.set(true);
					synchronized (this) {
						waiters.remove(waiter);
					}
				});
			});
		}

		private void release() {
			while (true) {
				final Waiter waiter;
				synchronized (this) {
					waiter = waiters.pollFirst();
					if (waiter == null) {
						available++;
						return;
					}
				}

				if (!waiter.cancelled.get()
					&& waiter.sink.tryEmitValue(new Permit(this)).isSuccess()) {
					return;
				}
			}
		}

		private static final class Waiter {
			private final Sinks.One<Permit> sink = Sinks.one();
			private final AtomicBoolean cancelled = new AtomicBoolean();
		}
	}

	private static final class Permit {
		private final PermitPool owner;
		private final AtomicBoolean released = new AtomicBoolean();

		private Permit(PermitPool owner) {
			this.owner = owner;
		}

		private void release() {
			if (released.compareAndSet(false, true)) {
				owner.release();
			}
		}
	}
}
