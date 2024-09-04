package services.k_int.utils.reactor;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import reactor.core.publisher.Mono;

public class LoggerAwareMonoSideEffects<T> {

	private Mono<T> f;
	private final Logger log;
	
	public static <T> Function<Mono<T>, LoggerAwareMonoSideEffects<T>> withLogger ( Logger log ) {
		return f -> new LoggerAwareMonoSideEffects<T>(f, log);
	}
	
	private LoggerAwareMonoSideEffects( Mono<T> f, Logger log ) {
		this.log = log;
		this.f = f;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnSubscribe( Level level, Consumer<? super Subscription> onSubscribe ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnSubscribe( onSubscribe );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnNext( Level level, Consumer<? super T> onNext ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnNext( onNext );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnError( Level level, Consumer<? super Throwable> onError ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnError( onError );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnSuccess( Level level, Consumer<? super T> onComplete ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnSuccess( onComplete );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnTerminate( Level level, Runnable onTerminate ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnTerminate( onTerminate );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doAfterTerminate( Level level, Runnable afterTerminate ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doAfterTerminate( afterTerminate );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnRequest( Level level, LongConsumer onRequest ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnRequest( onRequest );
		}
		return this;
	}
	
	public LoggerAwareMonoSideEffects<T> doOnCancel( Level level, Runnable onCancel ) {
		if (log.isEnabledForLevel(level)) {
			f = f.doOnCancel( onCancel );
		}
		return this;
	}
	
	public Mono<T> asMono() {
		return f;
	}
}
