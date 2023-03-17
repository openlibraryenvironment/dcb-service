package services.k_int.micronaut;

import org.reactivestreams.Publisher;

@FunctionalInterface
public interface PublisherTransformation<T> {
	Publisher<T> apply ( Publisher<T> target );
}
