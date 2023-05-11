package org.olf.reshare.dcb.test;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class PublisherUtils {
	private PublisherUtils() { }


	public static <T> T singleValueFrom(Publisher<T> publisher) {
		return Mono.from(publisher).block();
	}
}
