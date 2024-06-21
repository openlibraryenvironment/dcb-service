package services.k_int.utils;

import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactorUtils {
	
	static final Logger log = LoggerFactory.getLogger(ReactorUtils.class);
	
	public static <T> Flux<T> fluxFromPageableMethod ( Function<Pageable, Publisher<Page<T>>> pub, Pageable pageable ) {
		return Mono.just( pageable )
			.expand( p -> {
				final Pageable next = p.next();
				log.info("Next page [{}]", next);
				return Mono.just(next);
			})
			.concatMap( p-> {
				return pub.apply(p);
			} )
			.flatMap( Flux::fromIterable )
		;
	}
	
	public static <T> Flux<T> fluxFromPageableMethod ( Function<Pageable, Publisher<Page<T>>> pub, int pageSize, int pageStart ) {
		return fluxFromPageableMethod( pub, Pageable.from(pageStart, pageSize) );
	}

	public static <T> Flux<T> fluxFromPageableMethod ( Function<Pageable, Publisher<Page<T>>> pub, int pageSize ) {
		return fluxFromPageableMethod( pub, pageSize, 0);
	}
	
	public static <T> Flux<T> fluxFromPageableMethod ( Function<Pageable, Publisher<Page<T>>> pub ) {
		return fluxFromPageableMethod( pub, Pageable.from(0));
	}

	static <T> Consumer<T> consumeOnSuccess(Runnable onEmpty, Consumer<T> hasValue) {
		return value -> {
			if (value == null) {
				onEmpty.run();
			} else {
				hasValue.accept(value);
			}
		};
	}

	static <T> Mono<T> raiseError(Throwable throwable) {
		return Mono.defer(() -> Mono.error(throwable));
	}
}
