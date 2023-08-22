package services.k_int.utils;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Predicates {
	
	static final Logger log = LoggerFactory.getLogger(Predicates.class);
	
	public static <T, S> Predicate<T> failureLoggingPredicate( Predicate<T> p, BiConsumer<String, Object[]> logMethod, String logMessage, Object... logParams) {
		return p.or( (T val) -> { logMethod.accept(logMessage, logParams); return false; });
	}
	
	public static <T, S> Predicate<T> failureLoggingPredicate( Predicate<T> p, String message, Object... logParams) {
		return failureLoggingPredicate( p, log::info, message, logParams);
	}
}
