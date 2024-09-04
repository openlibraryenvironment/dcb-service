package services.k_int.utils.reactor;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import reactor.core.publisher.Flux;

public class LoggerAwareFluxSideEffects<T> {

	private Flux<T> f;
	private final Logger log;
	
	public static <T> Function<Flux<T>, LoggerAwareFluxSideEffects<T>> withLogger ( Logger log ) {
		return f -> new LoggerAwareFluxSideEffects<T>(f, log);
	}
	
	private LoggerAwareFluxSideEffects( Flux<T> f, Logger log ) {
		this.f = f;
		this.log = log;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnSubscribe( Level level, Consumer<? super Subscription> onSubscribe ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnSubscribe( onSubscribe );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnNext( Level level, Consumer<? super T> onNext ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnNext( onNext );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnError( Level level, Consumer<? super Throwable> onError ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnError( onError );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnComplete( Level level, Runnable onComplete ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnComplete( onComplete );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnTerminate( Level level, Runnable onTerminate ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnTerminate( onTerminate );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doAfterTerminate( Level level, Runnable afterTerminate ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doAfterTerminate( afterTerminate );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnRequest( Level level, LongConsumer onRequest ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnRequest( onRequest );
		}
		return this;
	}
	
	public LoggerAwareFluxSideEffects<T> doOnCancel( Level level, Runnable onCancel ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnCancel( onCancel );
		}
		return this;
	}
	
	public Flux<T> asFlux() {
		return f;
	}
}
