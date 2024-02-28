package services.k_int.federation.reactor;

public class UnableToRelinquishLockException extends RuntimeException {
	private static final long serialVersionUID = -2146107985860543531L;

	public static UnableToRelinquishLockException of(String message) {
		return new UnableToRelinquishLockException(message, null);
	}
	
	public static UnableToRelinquishLockException of(String message, Throwable cause) {
		return new UnableToRelinquishLockException(message, cause);
	}

	private UnableToRelinquishLockException(String message, Throwable cause) {
		super(message, cause, false, false);
	}
}
