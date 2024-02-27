package services.k_int.federation.reactor;

public class UnableToObtainLockException extends RuntimeException {
	private static final long serialVersionUID = -2146107985860543531L;

	public static UnableToObtainLockException of(String message) {
		return new UnableToObtainLockException(message);
	}

	private UnableToObtainLockException(String message) {
		super(message, null, false, false);
	}
}
