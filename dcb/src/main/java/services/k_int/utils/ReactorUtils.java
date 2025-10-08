package services.k_int.utils;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.reactor.LoggerAwareFluxSideEffects;
import services.k_int.utils.reactor.LoggerAwareMonoSideEffects;

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
	
	public static <T> Function<Mono<T>,Mono<T>> withMonoLogging ( Logger logger, Consumer<LoggerAwareMonoSideEffects<T>> logSideEffects ) {
		return m -> {
			final var sideEffects = m.as(logAwareMono( logger ));
			
			logSideEffects.accept(sideEffects);
			
			return sideEffects
				.asMono();
		};
			
	}

	public static <T> Function<Flux<T>,Flux<T>> withFluxLogging ( Logger logger, Consumer<LoggerAwareFluxSideEffects<T>> logSideEffects ) {
		return f -> {
			final var sideEffects = f.as(logAwareFlux( logger ));
			
			logSideEffects.accept(sideEffects);
			
			return sideEffects
				.asFlux();
		};
			
	}
	
	public static <T> Function<Mono<T>, LoggerAwareMonoSideEffects<T>> logAwareMono( Logger logger ) {
		return LoggerAwareMonoSideEffects.withLogger(logger);
	}

	public static <T> Function<Flux<T>, LoggerAwareFluxSideEffects<T>> logAwareFlux( Logger logger ) {
		return LoggerAwareFluxSideEffects.withLogger(logger);
	}

	static <TMain, TRelated> Mono<TMain> fetchRelatedRecord(TMain mainRecord,
		Function<TMain, Mono<? extends TRelated>> relatedRecordFinder,
		BiFunction<TMain, TRelated, TMain> combinator) {

		return Mono.just(mainRecord)
			.zipWhen(relatedRecordFinder, combinator)
			.switchIfEmpty(Mono.just(mainRecord));
	}
}
