package org.olf.dcb.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CollectionUtils {
	public static <T> List<T> nonNullValuesList(T... values) {
		return Stream.of(values)
			.filter(Objects::nonNull)
			.toList();
	}

	public static <T> Object[] iterableToArray(Iterable<T> iterable) {
		if (iterable == null) {
			return null;
		}

		final List<T> list = new ArrayList<>();

		iterable.forEach(list::add);

		return list.size() > 0 ? list.toArray() : null;
	}

	public static <T> Collection<T> nullIfEmpty(Collection<T> collection) {
		if (collection == null || collection.size() < 1) {
			return null;
		}

		return collection;
	}
}
