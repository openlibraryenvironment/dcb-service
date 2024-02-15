package org.olf.dcb.utils;

import static java.util.function.Function.identity;

import java.util.Optional;
import java.util.function.Function;

public class PropertyAccessUtils {
	public static <T, R> R getValue(T nullableObject, Function<T, R> accessor) {
		return getValue(nullableObject, accessor, identity());
	}

	public static <T, R, S> S getValue(T nullableObject, Function<T, R> accessor,
		Function<R, S> mapper) {

		return Optional.ofNullable(nullableObject)
			.map(accessor)
			.map(mapper)
			.orElse(null);
	}
}
