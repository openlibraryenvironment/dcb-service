package org.olf.dcb.utils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CollectionUtils {
	public static <T> List<T> nonNullValuesList(T... values) {
		return Stream.of(values)
			.filter(Objects::nonNull)
			.toList();
	}
}
