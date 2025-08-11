package io.micronaut.data.r2dbc.transaction;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * TODO: This is not ideal. We should look at moving to the delegate pattern for this override or, remove it altogether when possible.  
 * @author Steve Osguthorpe
 */
@Slf4j
@EachBean(ConnectionFactory.class)
@Replaces(DefaultR2dbcReactorTransactionOperations.class)
public class ReplacementR2dbcReactorTransactionOperations
		implements ReactorReactiveTransactionOperations<Connection>, R2dbcReactorTransactionOperations {

	private final String dataSourceName;
	private final ReactorConnectionOperations<Connection> connectionOperations;

	ReplacementR2dbcReactorTransactionOperations(
			@Parameter String dataSourceName,
			@Parameter DefaultR2dbcReactorConnectionOperations connectionOperations) {
		this.dataSourceName = dataSourceName;
		this.connectionOperations = connectionOperations;

		if (log.isDebugEnabled()) {
			log.info("Using ReplacementR2dbcReactorTransactionOperations bean for datasource [{}]", dataSourceName);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public final Optional<ReactiveTransactionStatus<Connection>> findTransactionStatus( ContextView contextView ) {
		return ReactorPropagation.findAllContextElements(contextView, ReactiveTransactionPropagatedContext.class)
				.filter(e -> e.transactionOperations == this).map(e -> (ReactiveTransactionStatus<Connection>) e.status)
				.findFirst();
	}

	@Override
	public final ReactiveTransactionStatus<Connection> getTransactionStatus(ContextView contextView) {
		return findTransactionStatus(contextView).orElse(null);
	}

	@Override
	public final TransactionDefinition getTransactionDefinition(ContextView contextView) {
		ReactiveTransactionStatus<Connection> status = getTransactionStatus(contextView);
		return status == null ? null : status.getTransactionDefinition();
	}

	@Override
	@NonNull
	public final <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
			@NonNull TransactionalCallback<Connection, T> handler) {
		Objects.requireNonNull(definition, "Transaction definition cannot be null");
		Objects.requireNonNull(handler, "Callback handler cannot be null");

		return Flux.deferContextual(contextView -> {
			ReactiveTransactionStatus<Connection> transactionStatus = getTransactionStatus(contextView);
			return withTransactionFlux(transactionStatus, definition, handler);
		});
	}

	/**
	 * Execute the transaction with provided transaction status.
	 *
	 * @param transactionStatus The transaction status
	 * @param definition        The definition
	 * @param handler           The handler
	 * @param <T>               The transaction type
	 * @return The published result
	 */
	protected <T> Flux<T> withTransactionFlux(ReactiveTransactionStatus<Connection> transactionStatus,
			TransactionDefinition definition, TransactionalCallback<Connection, T> handler) {
		TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
		if (transactionStatus != null) {
			if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED
					|| propagationBehavior == TransactionDefinition.Propagation.NEVER) {
				return Flux.error(propagationNotSupported(propagationBehavior));
			}
			if (propagationBehavior == TransactionDefinition.Propagation.REQUIRES_NEW) {
				return openNewConnectionAndTx(definition, handler);
			}
			return executeCallbackFlux(existingTransaction(transactionStatus, definition), handler);
		}
		if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
			return Flux.error(expectedTransaction());
		}
		return openNewConnectionAndTx(definition, handler);
	}

	private <T> Flux<T> openNewConnectionAndTx(TransactionDefinition definition,
			TransactionalCallback<Connection, T> handler) {
		return connectionOperations.withConnectionFlux(definition.getConnectionDefinition(), connectionStatus -> {
			DefaultReactiveTransactionStatus<Connection> txStatus = new DefaultReactiveTransactionStatus<>(connectionStatus,
					true, definition);
			return executeTransactionFlux(txStatus, handler);
		});
	}

	@Override
	public <T> Mono<T> withTransactionMono(TransactionDefinition definition,
			Function<ReactiveTransactionStatus<Connection>, Mono<T>> handler) {
		Objects.requireNonNull(definition, "Transaction definition cannot be null");
		Objects.requireNonNull(handler, "Callback handler cannot be null");

		return Mono.deferContextual(contextView -> {
			ReactiveTransactionStatus<Connection> transactionStatus = getTransactionStatus(contextView);
			TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
			if (transactionStatus != null) {
				if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED
						|| propagationBehavior == TransactionDefinition.Propagation.NEVER) {
					return Mono.error(propagationNotSupported(propagationBehavior));
				}
				if (propagationBehavior == TransactionDefinition.Propagation.REQUIRES_NEW) {
					return openNewConnectionAndTxMono(definition, handler);
				}
				return executeCallbackMono(existingTransaction(transactionStatus, definition), handler);
			}
			if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
				return Mono.error(expectedTransaction());
			}
			return openNewConnectionAndTxMono(definition, handler);
		});
	}

	private <T> Mono<T> openNewConnectionAndTxMono(TransactionDefinition definition,
			Function<ReactiveTransactionStatus<Connection>, Mono<T>> handler) {
		return connectionOperations.withConnectionMono(definition.getConnectionDefinition(),
				connectionStatus -> executeTransactionMono(
						new DefaultReactiveTransactionStatus<>(connectionStatus, true, definition), handler));
	}

	/**
	 * Execute the transaction.
	 *
	 * @param txStatus The transaction status
	 * @param handler  The callback
	 * @param <R>      The callback result type
	 * @return The callback result
	 */
	@NonNull
	protected <R> Flux<R> executeTransactionFlux(@NonNull DefaultReactiveTransactionStatus<Connection> txStatus,
			@NonNull TransactionalCallback<Connection, R> handler) {
		ReactiveConnectionStatus<Connection> connectionStatus = (ReactiveConnectionStatus<Connection>) txStatus
				.getConnectionStatus();
		connectionStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
			@Override
			public Publisher<Void> onCancel() {
				return doCancel(txStatus);
			}
		});
		return Flux.from(new SyncCompleteAndErrorPublisher<>(
				Mono.fromDirect(beginTransaction(txStatus.getConnectionStatus(), txStatus.getTransactionDefinition()))
						.thenMany(Mono.just(txStatus)).flatMap(status -> executeCallbackFlux(status, handler)),
				() -> doCommit(txStatus), throwable -> doRollback(txStatus, throwable), false));
	}

	/**
	 * Execute the transaction.
	 *
	 * @param txStatus The transaction status
	 * @param handler  The callback
	 * @param <R>      The callback result type
	 * @return The callback result
	 */
	@NonNull
	protected <R> Mono<R> executeTransactionMono(@NonNull DefaultReactiveTransactionStatus<Connection> txStatus,
			@NonNull Function<ReactiveTransactionStatus<Connection>, Mono<R>> handler) {

		ReactiveConnectionStatus<Connection> connectionStatus = (ReactiveConnectionStatus<Connection>) txStatus
				.getConnectionStatus();
		connectionStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
			@Override
			public Publisher<Void> onCancel() {
				return doCancel(txStatus);
			}

		});
		return Mono.from(new SyncCompleteAndErrorPublisher<>(
				Mono.fromDirect(beginTransaction(txStatus.getConnectionStatus(), txStatus.getTransactionDefinition()))
						.thenReturn(txStatus)
						.flatMap(status -> {
							log.debug("Hopefully should propagate the status");
							return executeCallbackMono(status, handler);
						}),
				() -> doCommit(txStatus), throwable -> doRollback(txStatus, throwable), true));
	}

	/**
	 * Execute the callback.
	 *
	 * @param status  The transaction status
	 * @param handler The callback
	 * @param <R>     The callback result type
	 * @return The callback result
	 */
	@NonNull
	protected <R> Flux<R> executeCallbackFlux(@NonNull ReactiveTransactionStatus<Connection> status,
			@NonNull TransactionalCallback<Connection, R> handler) {
		try {
			
			return Flux.just( status )
//				.contextWrite(context -> addTxStatus(context, status))
				.flatMap( handler::doInTransaction )
				.contextWrite(context -> addTxStatus(context, status));
			
//			return Flux.from(handler.doInTransaction(status)).contextWrite(context -> addTxStatus(context, status));
		} catch (Exception e) {
			return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
		}
	}

	/**
	 * Execute the callback.
	 *
	 * @param status  The transaction status
	 * @param handler The callback
	 * @param <R>     The callback result type
	 * @return The callback result
	 */
	@NonNull
	protected <R> Mono<R> executeCallbackMono(@NonNull ReactiveTransactionStatus<Connection> status,
			@NonNull Function<ReactiveTransactionStatus<Connection>, Mono<R>> handler) {
		try {
			
			return Mono.just( status )
//				.contextWrite(context -> addTxStatus(context, status))
				.flatMap(handler::apply)
				.contextWrite(context -> addTxStatus(context, status));
			
//			return handler.apply(status).contextWrite(context -> addTxStatus(context, status));
		} catch (Exception e) {
			return Mono.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
		}
	}

	private ReactiveTransactionStatus<Connection> existingTransaction(ReactiveTransactionStatus<Connection> existing,
			TransactionDefinition transactionDefinition) {
		return new ReactiveTransactionStatus<>() {
			@Override
			public Connection getConnection() {
				return existing.getConnection();
			}

			@Override
			public ConnectionStatus<Connection> getConnectionStatus() {
				return existing.getConnectionStatus();
			}

			@Override
			public boolean isNewTransaction() {
				return false;
			}

			@Override
			public void setRollbackOnly() {
				existing.setRollbackOnly();
			}

			@Override
			public boolean isRollbackOnly() {
				return existing.isRollbackOnly();
			}

			@Override
			public boolean isCompleted() {
				return existing.isCompleted();
			}

			@Override
			public TransactionDefinition getTransactionDefinition() {
				return transactionDefinition;
			}
		};
	}

	/**
	 * Cancels the TX operation.
	 *
	 * @param status The TX status
	 * @return the canceling publisher
	 */
	@NonNull
	protected Publisher<Void> doCancel(@NonNull DefaultReactiveTransactionStatus<Connection> status) {
		// Default behaviour is to commit the TX
		return doCommit(status);
	}

	@NonNull
	private Publisher<Void> doCommit(@NonNull DefaultReactiveTransactionStatus<Connection> status) {
		Flux<Void> op;
		try {
			if (status.isRollbackOnly()) {
				op = Flux.from(rollbackTransaction(status.getConnectionStatus(), status.getTransactionDefinition()));
			} else {
				op = Flux.from(commitTransaction(status.getConnectionStatus(), status.getTransactionDefinition()));
			}
		} catch (Exception e) {
			// Sometimes an exception can be thrown creating the publishers for rollback or commit.
			// An example of this, is if the connection has been closed prematurely by the DBMS.
			// We should ensure we always return a Publisher to allow downstream consumers to properly
			// detect and handle these type of errors.
			op = Flux.error(e);
		}
		return op.as(flux -> doFinish(flux, status));
	}

	@NonNull
	private Publisher<Void> doRollback(@NonNull DefaultReactiveTransactionStatus<Connection> status,
			@NonNull Throwable throwable) {
		if (log.isWarnEnabled()) {
			log.warn("Rolling back transaction on error: " + throwable.getMessage(), throwable);
		}
		Flux<Void> abort;
		try {
			TransactionDefinition definition = status.getTransactionDefinition();
			if (definition.rollbackOn(throwable)) {
				abort = Flux.from(rollbackTransaction(status.getConnectionStatus(), definition));
			} else {
				abort = Flux.error(throwable);
			}
		} catch (Exception e) {
			// Sometimes an exception can be thrown creating the publishers for rollback or commit.
			// An example of this, is if the connection has been closed prematurely by the DBMS.
			// We should ensure we always return a Publisher to allow downstream consumers to properly
			// detect and handle these type of errors.
			abort = Flux.error(e);
		}
		
		return abort.onErrorResume((rollbackError) -> {
			if (rollbackError != throwable && log.isWarnEnabled()) {
				log.warn("Error occurred during transaction rollback: " + rollbackError.getMessage(), rollbackError);
			}
			return Mono.error(throwable);
		}).as(flux -> doFinish(flux, status));
	}

	private <T> Publisher<Void> doFinish(Flux<T> flux, DefaultReactiveTransactionStatus<Connection> status) {
		return flux.hasElements().map(ignore -> {
			status.completed = true;
			return ignore;
		}).then();
	}

	@NonNull
	private Context addTxStatus(@NonNull Context context, @NonNull ReactiveTransactionStatus<Connection> status) {
		return ReactorPropagation.addContextElement(context, new ReactiveTransactionPropagatedContext<>(this, status));
	}

	@NonNull
	private NoTransactionException expectedTransaction() {
		return new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context.");
	}

	@NonNull
	private TransactionUsageException propagationNotSupported(TransactionDefinition.Propagation propagationBehavior) {
		return new TransactionUsageException(
				"Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior);
	}

	private record ReactiveTransactionPropagatedContext<C>(
			ReactiveTransactionOperations<?> transactionOperations, ReactiveTransactionStatus<C> status)
			implements PropagatedContextElement {
	}

	/**
	 * Represents the current reactive transaction status.
	 *
	 * @param <Connection> The connection type
	 */
	protected static final class DefaultReactiveTransactionStatus<C>
			implements ReactiveTransactionStatus<C> {
		private final ConnectionStatus<C> connectionStatus;
		private final boolean isNew;
		private final TransactionDefinition transactionDefinition;
		private boolean rollbackOnly;
		private boolean completed;

		public DefaultReactiveTransactionStatus(ConnectionStatus<C> connectionStatus, boolean isNew,
				TransactionDefinition transactionDefinition) {
			this.connectionStatus = connectionStatus;
			this.isNew = isNew;
			this.transactionDefinition = transactionDefinition;
		}

		@Override
		public ConnectionStatus<C> getConnectionStatus() {
			return connectionStatus;
		}

		@Override
		public TransactionDefinition getTransactionDefinition() {
			return transactionDefinition;
		}

		@Override
		public C getConnection() {
			return connectionStatus.getConnection();
		}

		@Override
		public boolean isNewTransaction() {
			return isNew;
		}

		@Override
		public void setRollbackOnly() {
			this.rollbackOnly = true;
		}

		@Override
		public boolean isRollbackOnly() {
			return rollbackOnly;
		}

		@Override
		public boolean isCompleted() {
			return completed;
		}
	}

	protected Publisher<Void> beginTransaction(ConnectionStatus<Connection> connectionStatus,
			TransactionDefinition definition) {
		Connection connection = connectionStatus.getConnection();
		if (log.isDebugEnabled()) {
			log.debug("Transaction begin for R2DBC connection: {} and configuration {}.", connection, dataSourceName);
		}
		Flux<Void> result = Flux.empty();
		if (definition.getTimeout().isPresent()) {
			Duration timeout = definition.getTimeout().get();
			if (log.isDebugEnabled()) {
				log.debug("Setting statement timeout ({}) for transaction: {} for dataSource: {}", timeout,
						definition.getName(), dataSourceName);
			}
			result = result.thenMany(connection.setStatementTimeout(timeout));
		}
		if (definition.getIsolationLevel().isPresent()) {
			IsolationLevel isolationLevel = getIsolationLevel(definition);
			if (log.isDebugEnabled()) {
				log.debug("Setting Isolation Level ({}) for transaction: {} for dataSource: {}", isolationLevel,
						definition.getName(), dataSourceName);
			}
			if (isolationLevel != null) {
				result = result.thenMany(connection.setTransactionIsolationLevel(isolationLevel));
			}
		}
		return result.thenMany(connection.beginTransaction());
	}

	protected Publisher<Void> commitTransaction(ConnectionStatus<Connection> connectionStatus,
			TransactionDefinition transactionDefinition) {
		if (log.isDebugEnabled()) {
			log.debug("Committing transaction for R2DBC connection: {} and configuration {}.",
					connectionStatus.getConnection(), dataSourceName);
		}
		return connectionStatus.getConnection().commitTransaction();
	}

	protected Publisher<Void> rollbackTransaction(ConnectionStatus<Connection> connectionStatus,
			TransactionDefinition transactionDefinition) {
		if (log.isDebugEnabled()) {
			log.debug("Rolling back transaction for R2DBC connection: {} and configuration {}.",
					connectionStatus.getConnection(), dataSourceName);
		}
		return connectionStatus.getConnection().rollbackTransaction();
	}

	private IsolationLevel getIsolationLevel(TransactionDefinition definition) {
		return definition.getIsolationLevel().map(isolation -> switch (isolation) {
		case READ_COMMITTED -> IsolationLevel.READ_COMMITTED;
		case READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED;
		case REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ;
		case SERIALIZABLE -> IsolationLevel.SERIALIZABLE;
		default -> null;
		}).orElse(null);
	}

	@Override
	public <T> Publisher<T> withTransaction(ReactiveTransactionStatus<Connection> status,
			TransactionDefinition definition, TransactionalCallback<Connection, T> handler) {
		return withTransactionFlux(status, definition, handler);
	}

	private static final class SyncCompleteAndErrorPublisher<T> implements CorePublisher<T> {

		private final CorePublisher<T> actualPublisher;
		private final Supplier<Publisher<Void>> onComplete;
		private final Function<Throwable, Publisher<Void>> onThrowable;
		private final boolean isMono;

		SyncCompleteAndErrorPublisher(CorePublisher<T> actualPublisher, Supplier<Publisher<Void>> onComplete,
				Function<Throwable, Publisher<Void>> onThrowable, boolean isMono) {
			this.actualPublisher = actualPublisher;
			this.onComplete = onComplete;
			this.onThrowable = onThrowable;
			this.isMono = isMono;
		}

		@Override
		public void subscribe(CoreSubscriber<? super T> actualSubscriber) {
			doSubscribe(actualSubscriber, actualSubscriber);
		}

		@Override
		public void subscribe(Subscriber<? super T> actualSubscriber) {
			if (actualSubscriber instanceof CoreSubscriber<? super T> coreSubscriber) {
				doSubscribe(actualSubscriber, coreSubscriber);
			} else {
				doSubscribe(actualSubscriber, null);
			}
		}

		private void doSubscribe(Subscriber<? super T> actualSubscriber,
				@Nullable CoreSubscriber<? super T> coreSubscriber) {
			actualPublisher.subscribe(new CoreSubscriber<>() {

				Subscription actualSubscription;

				@Override
				public Context currentContext() {
					if (coreSubscriber == null) {
						return Context.empty();
					}
					return coreSubscriber.currentContext();
				}

				@Override
				public void onSubscribe(Subscription s) {
					actualSubscription = s;
					actualSubscriber.onSubscribe(s);
				}

				@Override
				public void onNext(T t) {
					if (isMono) {
						actualSubscription.cancel();
						onComplete.get().subscribe(new Subscriber<>() {
							@Override
							public void onSubscribe(Subscription s) {
								s.request(1);
							}

							@Override
							public void onNext(Void unused) {

							}

							@Override
							public void onError(Throwable t) {
								actualSubscriber.onError(t);
							}

							@Override
							public void onComplete() {
								actualSubscriber.onNext(t);
								actualSubscriber.onComplete();
							}
						});
					} else {
						actualSubscriber.onNext(t);
					}
				}

				@Override
				public void onError(Throwable throwable) {
					onThrowable.apply(throwable).subscribe(new Subscriber<>() {
						@Override
						public void onSubscribe(Subscription s) {
							s.request(1);
						}

						@Override
						public void onNext(Void unused) {

						}

						@Override
						public void onError(Throwable t) {
							actualSubscriber.onError(t);
						}

						@Override
						public void onComplete() {
							actualSubscriber.onError(throwable);

						}
					});
				}

				@Override
				public void onComplete() {
					onComplete.get().subscribe(new Subscriber<>() {
						@Override
						public void onSubscribe(Subscription s) {
							s.request(1);
						}

						@Override
						public void onNext(Void unused) {

						}

						@Override
						public void onError(Throwable t) {
							actualSubscriber.onError(t);
						}

						@Override
						public void onComplete() {
							actualSubscriber.onComplete();
						}
					});
				}

			});
		}

	}
}
