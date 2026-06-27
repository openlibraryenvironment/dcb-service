package services.k_int.federation.local;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import services.k_int.federation.FederatedLockService;

@Singleton
@Slf4j
@Named("local")
@Requires(property = "dcb.federation.lock-provider", value = "local")
public class LocalFederatedLockService implements FederatedLockService {
	private static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

	@Override
	public Lock getNamedLock(String lockName) {
		return LOCKS.computeIfAbsent(lockName, ignored -> new ReentrantLock());
	}

	@Override
	public void federatedLockAndDoWithTimeout(String lockName, long timeoutMillis, Runnable work)
		throws TimeoutException {

		Lock lock = getNamedLock(lockName);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
			if (!acquired) {
				throw new TimeoutException("Exhausted the timeout waiting for a local lock.");
			}
			work.run();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TimeoutException("Interrupted while waiting for a local lock.");
		}
		finally {
			if (acquired) {
				lock.unlock();
			}
		}
	}

	@Override
	public void federatedLockAndDo(String lockName, Runnable work) {
		Lock lock = getNamedLock(lockName);
		lock.lock();
		try {
			work.run();
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public boolean federatedLockAndDoWithTimeoutOrSkip(String lockName, long timeoutMillis, Runnable work) {
		try {
			federatedLockAndDoWithTimeout(lockName, timeoutMillis, work);
			return true;
		}
		catch (TimeoutException e) {
			log.debug("Could not obtain local lock [{}]: {}", lockName, e.getMessage());
			return false;
		}
	}

	@Override
	public void waitForNoFederatedLock(String lockName) {
		Lock lock = getNamedLock(lockName);
		lock.lock();
		lock.unlock();
	}

	@Override
	public boolean waitMaxForNoFederatedLock(String lockName, long timeoutMillis) {
		Lock lock = getNamedLock(lockName);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
			return acquired;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		finally {
			if (acquired) {
				lock.unlock();
			}
		}
	}
}
