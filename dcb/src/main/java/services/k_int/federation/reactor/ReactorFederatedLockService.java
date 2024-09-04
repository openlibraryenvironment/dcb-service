package services.k_int.federation.reactor;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import services.k_int.federation.FederatedLockService;

@Slf4j
@Singleton
public class ReactorFederatedLockService {

	private static final String SCHEDULER_NAME = "reactor-lock-manager-";
	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final FederatedLockService lockService;
	private final Scheduler lockScheduler;

	public ReactorFederatedLockService(FederatedLockService lockService) {
		this.lockService = lockService;
		
		// Custom threading. Single executor to reuse 1 thread for internal control.
		lockScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadScheduledExecutor(new	ThreadFactory() {
		    public Thread newThread(Runnable runnable) {
		        return new Thread(runnable, SCHEDULER_NAME + threadNumber.getAndIncrement());
		    }
		}));
	}

	private Lock lockOrFail(Lock lock) throws UnableToObtainLockException {
		if (!lock.tryLock())
			throw UnableToObtainLockException.of("Could not obtain lock");
		return lock;
	}

	private Lock relinquish(Lock lock) throws UnableToRelinquishLockException {
		try {
			lock.unlock();
			return lock;
		} catch (Throwable t) {
			throw UnableToRelinquishLockException.of("Could not relinquish lock", t);
		}
	}

	/**
	 * Publisher transformer that attempts to obtained a a unique lock across the federation when the target publisher is subscribed to.
	 * If a lock cannot be obtained then the transformed publisher will complete with an error.
	 * You should aim to call this transformer just after the last operator you wish to protect with the lock. 
	 * 
	 * @throws UnableToRelinquishLockException
	 * @param <T>
	 * @param lockName
	 * @return
	 */
	public <T> Function<Publisher<T>, Publisher<T>> withLock(@NonNull String lockName) {
		return (source) -> {

			// Create a single thread Scheduler, as the locks need to be owned by the
			// thread that wishes to relinquish after obtaining the lock.
			final Lock lock = lockService.getNamedLock(lockName);

			// Lazily get a reference to the named lock.
			return Mono.defer(() -> Mono.just(lock))
				.publishOn(lockScheduler)
				.map(this::lockOrFail) // Mono emitting on next can be assumed as the start of the subscription.
				.flatMapMany(_lock -> source) // Play the original publisher.
				.publishOn(lockScheduler) // Ensure the publisher uses the owning thread to relinquish the lock.
				.doFinally(_signal -> {
					relinquish(lock);
					log.debug("Relinquished lock[{}]", lockName);
				});
		};

	}

	/**
	 * Publisher transformer that attempts to obtained a a unique lock across the federation when the target publisher is subscribed to.
	 * If a lock cannot be obtained then the transformed publisher will be an empty publisher.
	 * You should aim to call this transformer just after the last operator you wish to protect with the lock. 
	 * 
	 * @param <T>
	 * @param lockName The Lock Name
	 * @return The transformed publisher
	 */
	public <T> Function<Publisher<T>, Publisher<T>> withLockOrEmpty(@NonNull String lockName) {
		return (source) -> {

			// Create a single thread Scheduler, as the locks need to be owned by the
			// thread that wishes to relinquish after obtaining the lock.
			final LockContext lockContext = new LockContext(lockService.getNamedLock(lockName));

			// Lazily get a reference to the named lock.
			return Mono.defer(() -> Mono.just(lockContext))
				.publishOn(lockScheduler)
				.mapNotNull(lockWrapper -> {
					
					lockContext.setObtained(lockWrapper.lock.tryLock());
					
					if (!lockWrapper.isObtained()) {
						log.debug("Could not obtain lock[{}], degrading to empty publisher", lockName);
						return null;
					}
					log.debug("Obtained lock[{}]", lockName);
					return lockWrapper.isObtained() ? lockWrapper : null;
				})
				.flatMapMany(_lock -> source) // Play the original publisher.
				.publishOn(lockScheduler) // Ensure the publisher uses the owning thread to relinquish the lock.
				.doFinally(_signal -> {
					if (lockContext.isObtained()) {
						relinquish(lockContext.getLock());
						log.debug("Relinquished lock[{}]", lockName);
					}
				});
		};
	}
	
	@Getter
	@Setter
	protected static class LockContext {
		private final Lock lock;
		private LockContext(Lock lock) {
			this.lock = lock;
		}
		private boolean obtained = false;
	}
}
