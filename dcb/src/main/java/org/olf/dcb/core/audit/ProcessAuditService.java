package org.olf.dcb.core.audit;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.olf.dcb.core.audit.ProcessAuditContext.ProcessAuditContextBuilder;
import org.olf.dcb.core.audit.model.ProcessAuditLogEntry;
import org.olf.dcb.storage.ProcessAuditRepository;
import org.reactivestreams.Publisher;
import org.slf4j.event.Level;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import services.k_int.utils.ReactorUtils;

@Slf4j
@Singleton
public class ProcessAuditService {
	
	private static final String KEY_AUDIT_CONTEXT = "AUDIT_CONTEXT";
	private final ProcessAuditRepository processAuditRepository;
	
	private final R2dbcOperations operations;
	public ProcessAuditService(R2dbcOperations operations, ProcessAuditRepository processAuditRepository) {
		this.processAuditRepository = processAuditRepository;
		this.operations = operations;
		
	}
	
	private static ProcessAuditContext getAuditContext( Context con ) {
		return con.get( KEY_AUDIT_CONTEXT );
	}
	
	
	private static Function<Context, Context> withAuditContext ( Function<ProcessAuditContextBuilder, ProcessAuditContextBuilder> build ) {
		return con -> {
			ProcessAuditContextBuilder builder;
			try {
				builder = Optional.ofNullable( getAuditContext( con ) )
					.map( ProcessAuditContext::toBuilder )
					.get();
				
				return con.put(KEY_AUDIT_CONTEXT, build.apply(builder).build());
			} catch (NoSuchElementException e) {
				log.error("Attempt to modify audit context, without it being set prior. Ensure there is a downstream `withNewProcessAudit` transformer");
				throw e;
			}
		};
	}
	
	private static Function<Context, Context> withNewAuditContext ( Function<ProcessAuditContextBuilder, ProcessAuditContextBuilder> build ) {
		return con -> con.put(KEY_AUDIT_CONTEXT, build.apply(ProcessAuditContext.builder()).build());
	}
	
	public static <T> Function<Publisher<T>, Publisher<T>> withNewProcessAudit( String processType ) {
		return withNewProcessAudit(processType, Function.identity());
	}
	
	public static <T> Function<Publisher<T>, Publisher<T>> withNewProcessAudit( String processType, Function<ProcessAuditContextBuilder, ProcessAuditContextBuilder> build ) {
		return process -> Flux.from( process )
			.contextWrite( withNewAuditContext( ac -> build.apply(ac.processType(processType)) ));
	}
	
	public <T> Function<Publisher<T>, Publisher<T>> withProcessAudit( Function<ProcessAuditContextBuilder, ProcessAuditContextBuilder> modifier ) {
		
		return process -> Mono.deferContextual( con -> {
			final ProcessAuditContext context = con.get(KEY_AUDIT_CONTEXT);
			return doAuditCleanup(context);
		})
		.transform(ReactorUtils.withMonoLogging(log, l -> l
			.doOnNext(Level.DEBUG, count -> log.trace("Cleaned up [{}] old process log entries", count))))
		
		.thenMany(Flux.from( process ))
		.contextWrite( withAuditContext( modifier ));
	}


	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<Long> doAuditCleanup ( @NotNull @NonNull @Valid ProcessAuditContext context ) {
		return Mono.from(processAuditRepository.deleteAllBySubjectIdAndProcessTypeAndProcessIdNot(context.getProcessSubject(), context.getProcessType(), context.getProcessId()));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<ProcessAuditLogEntry> doAuditEntry ( @NotNull @NonNull @Valid ProcessAuditLogEntry entry ) {
		
		return Mono.just(entry)
			.transform(ReactorUtils.withMonoLogging(log, l -> l
				.doOnNext(Level.TRACE, theEntry -> log.trace(theEntry.toString()))))
			.flatMapMany( processAuditRepository::save )
			.single();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected <T> Mono<T> doAuditMessage(T item, Function<T, String> messageCreator) {
		return Mono.deferContextual(con -> {
			return Mono.from(operations.withTransaction(trx -> {
				final ProcessAuditContext context = con.get(KEY_AUDIT_CONTEXT);
				final Mono<T> returnVal = Mono.just(item);
				
				if ( context == null ) {
					log.error("Attempt to add audit message without contextual process id, skipping message. Ensure there is a downstream `withNewProcessAudit` transformer");
					return returnVal;
				}
				
				String message = messageCreator.apply(item);
				var entry = ProcessAuditLogEntry.builder(context)
					.message(message)
					.build();
				
				return doAuditEntry( entry )
					.then(returnVal);
			}));
		});
	}
	
	public <T> Function<T, Mono<T>> withAuditMessage( Function<T, String> messageCreator ) {
		return item -> doAuditMessage(item, messageCreator);
	}
	
	public <T> Function<T, Mono<T>> withAuditMessage( Supplier<String> messageCreator ) {
		return withAuditMessage( _item -> messageCreator.get() );
	}
	
	public <T> Function<T, Mono<T>> withAuditMessage( String message ) {
		return withAuditMessage( _item -> message );
	}

	public Mono<String> auditMessage( String message ) {
		return Mono.just( message )
			.flatMap( withAuditMessage( message ) );
	}
	
	public Mono<String> auditMessage(Supplier<String> messageCreator) {
		return auditMessage( messageCreator.get() );
	}
	
	@NonNull
	@Transactional
	public Flux<ProcessAuditLogEntry> getProcessAudits(@NonNull UUID subjectId) {
		return Flux.from( processAuditRepository.findAllBySubjectId(subjectId) );
	}
}
