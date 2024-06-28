package org.olf.dcb.utils;

import static java.util.function.Function.identity;

import java.util.Optional;
import java.util.function.Function;

public class PropertyAccessUtils {
	public static <T, R> R getValue(T nullableObject, Function<T, R> accessor, R defaultValue) {
		return getValue(nullableObject, accessor, identity(), defaultValue);
	}

	public static <T, R, S> S getValue(T nullableObject, Function<T, R> accessor,
		Function<R, S> mapper, S defaultValue) {

		return Optional.ofNullable(nullableObject)
			.map(accessor)
			.map(mapper)
			.orElse(defaultValue);
	}

	public static String getValue(String nullableValue, String defaultValue) {
		return getValue(nullableValue, identity(), defaultValue);
	}

	public static <T, R> R getValueOrNull(T nullableObject, Function<T, R> accessor) {
		return getValueOrNull(nullableObject, accessor, identity());
	}

	public static <T, R, S> S getValueOrNull(T nullableObject, Function<T, R> accessor,
		Function<R, S> mapper) {

		return getValue(nullableObject, accessor, mapper, (S)null);
	}
}
