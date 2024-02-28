package services.k_int.hazelcast.federation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import services.k_int.federation.FederatedLockService;

@Singleton
@Slf4j
@Named("hazelcast")
public class HazelcastFederatedLockService implements FederatedLockService {

	private final HazelcastInstance hazelcast;
	private static final Map<String, FencedLock> lockCache = new ConcurrentHashMap<>(); 
	
	public HazelcastFederatedLockService(@NonNull HazelcastInstance hazelcast) {
		this.hazelcast = hazelcast;
	}
	
	public FencedLock getNamedLock ( @NonNull final String name ) {
		return lockCache.computeIfAbsent(name, hazelcast.getCPSubsystem()::getLock);
  }

	@Override
	public void federatedLockAndDoWithTimeout(String lockName, long timeoutMillis, Runnable work) throws TimeoutException {
		
		FencedLock lock = getNamedLock( lockName );
    
    try {
    	boolean acquiredLock = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
  		if (acquiredLock == false) throw new TimeoutException("Exhausted the timeout waiting for a federated lock.");

  		log.debug( "Thread[{}] acquired lock[{}]",	Thread.currentThread().getName(), lock.getName() );
      work.run();
    } finally {
    	releaseLockIfOwned(lock);
    }
	}
	
	private void releaseLockIfOwned( @NonNull FencedLock lock ) {
		if (!lock.isLockedByCurrentThread()) {
			
			// Not owned by this thread.
			if (lock.isLocked()) log.warn( "Thread[{}] attempted to release lock[{}], but lock is owned by another thread",
					Thread.currentThread().getName(), lock.getName() );
			return;
		}
		log.debug( "Thread[{}] relinquishing lock[{}]",	Thread.currentThread().getName(), lock.getName() );
		lock.unlock();
	}

	@Override
	public void federatedLockAndDo(String lockName, Runnable work) {
		FencedLock lock = getNamedLock( lockName );
    
    try {
    	lock.lock();
  		log.debug( "Thread[{}] acquired lock[{}]",	Thread.currentThread().getName(), lock.getName() );
      work.run();
    } finally {
    	releaseLockIfOwned(lock);
    }
	}

	@Override
	public boolean federatedLockAndDoWithTimeoutOrSkip(String lockName, long timeoutMillis, Runnable work) {
		FencedLock lock = getNamedLock( lockName );
    
    try {
    	boolean acquiredLock = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
  		if (acquiredLock == false) return false;

  		log.debug( "Thread[{}] acquired lock[{}]",	Thread.currentThread().getName(), lock.getName() );
      work.run();
    } finally {
    	releaseLockIfOwned(lock);
    }
		return true;
	}

	@Override
	public void waitForNoFederatedLock(String lockName) {
		FencedLock lock = getNamedLock( lockName );

		log.debug( "Thread[{}] waiting for lock[{}] to be relinquished",	Thread.currentThread().getName(), lock.getName() );
    while (lock.isLocked()) {
      log.debug ("Waiting for 1 second before retry...");
      try {
				Thread.sleep(1000);
			} catch (InterruptedException e) { /* NOOP */ }
    }
		log.debug( "lock[{}] relinquished",	lock.getName() );
	}

	@Override
	public boolean waitMaxForNoFederatedLock(String lockName, long timeoutMillis) {
		FencedLock lock = getNamedLock( lockName );
    
		final long start = System.currentTimeMillis();
		long now = start;
		log.debug( "Thread[{}] waiting for lock[{}] to be relinquished",	Thread.currentThread().getName(), lock.getName() );
    while (lock.isLocked() && (now - start) <= timeoutMillis) {
      log.debug ("Waiting for 1 second before retry...");
      try {
				Thread.sleep(1000);
				now = System.currentTimeMillis();
			} catch (InterruptedException e) { /* NOOP */ }
    }
    
    if (lock.isLocked()) {
    	log.debug( "lock[{}] was not relinquished within the timeout", lock.getName());
    	return false;
    }

		log.debug( "lock[{}] relinquished",	lock.getName() );
    
    return true;
	}
	
}
