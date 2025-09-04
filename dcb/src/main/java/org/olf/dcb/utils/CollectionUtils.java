package org.olf.dcb.utils;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import graphql.com.google.common.collect.Streams;

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

	public static <T> List<T> concatenate(Collection<T> firstCollection,
		Collection<T> secondCollection) {

		return Streams.concat(emptyWhenNull(firstCollection), emptyWhenNull(secondCollection)).toList();
	}

	public static <T> Stream<T> emptyWhenNull(Collection<T> collection) {
		if (collection == null) {
			return Stream.empty();
		}

		return collection.stream();
	}

	public static <R> R firstValueOrNull(Collection<R> collection) {
		return emptyWhenNull(collection).findFirst().orElse(null);
	}

	public static List<String> emptyListWhenNull(String value) {
		return value != null
			? nonNullValuesList(value)
			: emptyList();
	}
}
