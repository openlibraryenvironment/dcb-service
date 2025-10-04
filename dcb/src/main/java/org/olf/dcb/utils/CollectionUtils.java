package org.olf.dcb.utils;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import graphql.com.google.common.collect.Streams;

public class CollectionUtils {
	@SafeVarargs
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

		return list.isEmpty()
			? null
			: list.toArray();
	}

	public static <T> Collection<T> nullIfEmpty(Collection<T> collection) {
		if (collection == null || collection.isEmpty()) {
			return null;
		}

		return collection;
	}

	public static <T> List<T> concatenate(Collection<T> firstCollection,
		Collection<T> secondCollection) {

		return Streams.concat(emptyWhenNull(firstCollection), emptyWhenNull(secondCollection)).toList();
	}

	public static <T> Stream<T> emptyWhenNull(Collection<T> collection) {
		return getValue(collection, Collection::stream, Stream.empty());
	}

	public static <R> R firstValueOrNull(Collection<R> collection) {
		return emptyWhenNull(collection).findFirst().orElse(null);
	}

	public static <TSource, TDestination> List<TDestination> mapList(
		List<TSource> sourceList, Function<TSource, TDestination> mapper) {

		return mapStream(sourceList, mapper)
			.toList();
	}

	public static <TSource, TDestination> Stream<TDestination> mapStream(
		List<TSource> sourceList, Function<TSource, TDestination> mapper) {

		return emptyWhenNull(sourceList)
			.map(mapper);
	}
}
