package services.k_int.utils;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactorUtils {
	
	public static <T> Flux<T> fluxFromPageableMethod ( Function<Pageable, Publisher<Page<T>>> pub, Pageable pageable ) {
		return Mono.just( pageable )
			.expand( p -> Mono.just(p.next()) )
			.concatMap( pub::apply )
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
		return fluxFromPageableMethod( pub, Pageable.from(1));
	}

}
