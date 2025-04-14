package services.k_int.hazelcast.federation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import io.micronaut.core.annotation.NonNull;

public class NamedLockProxy implements Lock {
	private static final String KEY_FEDERATED_LOCKS = "federated-locks";
	private static final Map<String, Lock> localReentrantLocks = new ConcurrentHashMap<>();
	
	private final HazelcastInstance hazelcast;
	private final String lockName;
	private final String me;
	private final IMap<String,String> locks;
	
	public NamedLockProxy(@NonNull HazelcastInstance hazelcast, @NonNull String lockName) {
		this.hazelcast = hazelcast;
		this.lockName = lockName;
		this.me = hazelcast.getName();
		this.locks = hazelcast.getMap(KEY_FEDERATED_LOCKS);
	}
	
	@Override
	public void lock() {
		locks.lock(lockName);
	}
	@Override
	public void lockInterruptibly() throws InterruptedException {
		// TODO Auto-generated method stub
	}
	
	@Override
	public boolean tryLock() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public void unlock() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public Condition newCondition() {
		// TODO Auto-generated method stub
		return null;
	}
}
