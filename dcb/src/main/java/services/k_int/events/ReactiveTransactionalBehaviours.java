package services.k_int.events;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Singleton
public class ReactiveTransactionalBehaviours {
	
	private final R2dbcOperations operations;
	private static final Scheduler scheduler = Schedulers.boundedElastic();
	
	public ReactiveTransactionalBehaviours(R2dbcOperations operations) {
		this.operations = operations;
	}
	
	private static final Map<ReactiveTransactionStatus<?>, ConcurrentLinkedQueue<Runnable>> sideEffects = Collections.synchronizedMap( new WeakHashMap<>() );
	private static final Map<ReactiveTransactionStatus<?>, Disposable> trxWatchers = Collections.synchronizedMap( new WeakHashMap<>() );
	
	private static boolean noneRollbackPredicate(ReactiveTransactionStatus<?> s) {
		if (s.isRollbackOnly()) {
			log.info("Transaction [{}] rollback, skipping side-effects", s.toString());
			return false;
		}
		if (log.isTraceEnabled()) {
			log.trace("Transaction [{}] committal, running side-effects", s.toString());
		}
		
		return true;
	}
	
	@NonNull
	private static ConcurrentLinkedQueue<Runnable> getSideEffectQueueFor(@NonNull final ReactiveTransactionStatus<?> status) {
		ConcurrentLinkedQueue<Runnable> queue = sideEffects.computeIfAbsent(status, _s -> new ConcurrentLinkedQueue<>());
		
		// Create the listener for this transaction.
		trxWatchers.computeIfAbsent(status, sts -> {
			var watcher = Mono.just(sts)
				.publishOn( scheduler )
				.subscribeOn( scheduler )
				.repeat()
				.skipUntil( ReactiveTransactionStatus::isCompleted )
				
				// Wait until the transaction is completed.
				.next()
				
				// Rollbacks degrade to empty publisher
				.filter( ReactiveTransactionalBehaviours::noneRollbackPredicate )
				.mapNotNull( sideEffects::get )
				
				// Always tidy up.
				.doFinally( tidyQueues(sts) )
				.subscribe( handleEvent(sts), handleErrors(sts) );
				
				return watcher;
		});
		
		return queue;
	}
	
	private static Consumer<SignalType> tidyQueues(final ReactiveTransactionStatus<?> status) {

		return ( signal ) -> {
			log.trace("Clean up transactional event queues for [{}]", status);
			try {
				var watcher = trxWatchers.remove(status);
				if (watcher != null && !watcher.isDisposed()) {
					// Dispose the stream.
					watcher.dispose();
				}
			} catch (Throwable t) {
				/* NOOP */
			}

			try {
				var queue = sideEffects.remove(status);
				if (queue != null) {
					// Dispose the stream.
					queue.clear();
				}
			} catch (Throwable t) {
				/* NOOP */
			}
		};
	}
	
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	protected static Consumer<ConcurrentLinkedQueue<Runnable>> handleEvent(final ReactiveTransactionStatus<?> status) {
		
		return runnables -> {
			
			for (Runnable runnable : runnables) {
				try {
					runnable.run();
				} catch (Throwable t) {
					log.error("Error running [{}] as side effect for transaction [{}]", runnable, status);
				}
			}
		};
	}
	
	private static Consumer<Throwable> handleErrors(final ReactiveTransactionStatus<?> status) {
		return ( t ) ->	log.error("Error running side-effects for transaction [%s]".formatted(status), t);
	}
	
	/**
	 * <p>Publisher transformer to register a single side-effect to be triggered upon successful
	 * committal of the currently scoped transaction. Use in transform style elements to
	 * register a <strong>single side-effect per Publisher</strong> that will fire once the transaction
	 * has been committed successfully and NOT on rollback.</p>
	 * 
	 * @param <T> Publisher element type
	 * @param callable The callable to run after transaction commit 
	 * @return The original publisher
	 */
	public <T> Function<Publisher<T>, Publisher<T>> doOnCommittal(Runnable runnable) {
		
		return original -> Flux.just( original )
			// We use flatMap to ensure the registration happens before we progress with
			// the subscription.
			.flatMap( pub -> flatMapRunnable( pub, runnable ) )
			.flatMap(Flux::from);
	}
	
	/**
	 * <p>Publisher transformer to register side-effects to be triggered upon successful
	 * committal of the currently scoped transaction. Use in transform style elements to
	 * register a <strong>side-effect per published element</strong> that will fire once
	 * the transaction has been committed successfully and NOT on rollback.</p>
	 * 
	 * @param <T> Publisher element type
	 * @param callable The callable to run after transaction commit 
	 * @return The original element
	 */
	public <T> Function<Publisher<T>, Publisher<T>> doOnCommittal(Consumer<T> consumer) {
		
		return original -> Flux.from( original )
			// We use flatMap to ensure the registration happens before we progress with
			// the subscription.
			.flatMap( el -> flatMapConsumer( el, consumer ));
	}
	
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Publisher<T> flatMapConsumer( T element, Consumer<T> consumer ) {
		Runnable runnable = runConsumer(consumer, element);
		return flatMapRunnable( element, runnable );
	}
	
	@ExecuteOn(TaskExecutors.BLOCKING)
	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Publisher<T> flatMapRunnable( T element, Runnable runnable ) {
		return operations.withTransaction(status -> { 
			// Create runnable with this element and register it.
			getSideEffectQueueFor(status).add(runnable);
			
			return Mono.just(element);
		});
	}
	
	private static <T> Runnable runConsumer (Consumer<T> consumer, T toConsume) {
		return () -> {
			try {
				consumer.accept(toConsume);
			} catch (Throwable t) {
				if (RuntimeException.class.isAssignableFrom(t.getClass())) {
					throw t;
				}
				throw new RuntimeException(t);
			}
		};
	}
	
}
