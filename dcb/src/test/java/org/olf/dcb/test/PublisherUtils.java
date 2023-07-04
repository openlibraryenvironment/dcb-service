package org.olf.dcb.test;

import java.util.List;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PublisherUtils {
	private PublisherUtils() { }

	public static <T> T singleValueFrom(Publisher<T> publisher) {
		return Mono.from(publisher).block();
	}

	public static <T> List<T> manyValuesFrom(Publisher<T> publisher) {
		return Flux.from(publisher).collectList().block();
	}
}
