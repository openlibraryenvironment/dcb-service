package services.k_int.federation;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

public interface FederatedLockService {
	
	/**
	 * Get the named Lock
	 * @param lockName the Lock name
	 * @return the Lock with the supplied name
	 */
	public Lock getNamedLock( String lockName );
	
	/**
   * Attempts to get a named lock across the federation.
   * 
   * If the timeout is exceeded before a lock can be acquired, a TimeoutException is thrown. The
   * retry time is a minimum of 1000 milli seconds so actual times can be + 1 second.
   * 
   * @param lockName the Lock name
   * @param timeoutMillis Timeout duration in milliseconds
   * @param work Runnable work unit to do once the lock has been obtained.
   * @throws TimeoutException
   */
  public void federatedLockAndDoWithTimeout( final String lockName, long timeoutMillis, final Runnable work) throws TimeoutException;
  
  /**
   * Block this thread indefinitely until the lock is obtained.
   * WARNING: Use this sparingly and be sure you know what you are doing.
   * 
   * @param lockName the Lock name
   * @param work Runnable work unit to do once the lock has been obtained.
   */
  public void federatedLockAndDo ( final String lockName, Runnable work );
  
  /**
   * Attempts to get a named lock across the federation.
   * 
   * If the timeout is exceeded before a lock can be acquired the work is not carried out
   * and a boolean false is returned from this method.
   * 
   * @param lockName the Lock name
   * @param timeoutMillis Timeout duration in milliseconds
   * @param work Runnable work unit to do once the lock has been obtained.
   * @return TRUE if the lock was acquired and the work completed, FALSE otherwise
   */
  public boolean federatedLockAndDoWithTimeoutOrSkip( final String lockName, long timeoutMillis, final Runnable work);
  
  
  /**
   * Waits (and Blocks) indefinitely until the named lock is relinquished across the federation
   * @param lockName the Lock name
   */
  public void waitForNoFederatedLock( final String lockName );
  
  
  /**
   * @param lockName the Lock name
   * @param timeoutMillis Timeout duration in milliseconds
   * @return TRUE if the lock is eventually relinquished, FALSE otherwise
   */
  public boolean waitMaxForNoFederatedLock( final String lockName, long timeoutMillis);
}
