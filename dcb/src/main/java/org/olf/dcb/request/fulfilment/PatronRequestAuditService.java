package org.olf.dcb.request.fulfilment;

import static java.util.Optional.empty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PatronRequestAuditService {
	private final PatronRequestAuditRepository patronRequestAuditRepository;
	private final PatronRequestRepository patronRequestRepository;

	public PatronRequestAuditService(PatronRequestAuditRepository patronRequestAuditRepository,
		PatronRequestRepository patronRequestRepository) {

		this.patronRequestAuditRepository = patronRequestAuditRepository;
		this.patronRequestRepository = patronRequestRepository;
	}

	private void log(PatronRequestAudit auditEntry) {
		log.debug("AUDIT LOG {}: {}",
			auditEntry.getToStatus() == ERROR ? "Unsuccessful transition" : "Successful transition",
			auditEntry);
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		Status from, Status to) {

		return addAuditEntry(patronRequest, from, to, empty(), empty());
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		Status from, Status to, Optional<String> message) {

		return addAuditEntry(patronRequest, from, to, message, empty());
	}

	public Mono<RequestWorkflowContext> addAuditEntry(RequestWorkflowContext context,
		Status from, Status to, Optional<String> message, Optional<Map<String, Object>> auditData) {

		return addAuditEntry(context.getPatronRequest(), from, to, message, auditData)
			.thenReturn(context);
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		Status from, Status to, Optional<String> message, Optional<Map<String, Object>> auditData) {

		var builder = PatronRequestAudit.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.auditDate(Instant.now())
			.fromStatus(from)
			.toStatus(to)
			.auditData(auditData.orElse(null));

		message.ifPresent(value -> {
			String trimmedValue = value.substring(0, Math.min(value.length(), 254));
			builder.briefDescription(trimmedValue);
		});

		return buildAndSaveAuditMessage( builder );
	}
	
	@Transactional
	protected Mono<PatronRequestAudit> buildAndSaveAuditMessage(PatronRequestAudit.PatronRequestAuditBuilder builder) {
		
		PatronRequestAudit pra = builder.build();

		return Mono.just(pra)
			.flatMap(auditEntry -> Mono.from(patronRequestAuditRepository.save(auditEntry))
				.cast(PatronRequestAudit.class))
			.doOnSuccess(this::log)
			.doOnError(error -> log.error("Error attempting to write audit for {}", pra, error));
	}

	public Mono<PatronRequestAudit> addAuditEntry(UUID patronRequestId,
		String message, Map<String, Object> auditData) {

		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap(pr -> this.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
				Optional.ofNullable(message), Optional.ofNullable(auditData)));
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest pr, String message) {
		return this.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
			Optional.ofNullable(message), empty());
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest pr,
		String message, Map<String, Object> auditData) {

		return this.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
			Optional.ofNullable(message), Optional.ofNullable(auditData));
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest,
		String message) {

		return addAuditEntry(patronRequest, patronRequest.getStatus(), ERROR,
			Optional.ofNullable(message), empty());
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(
		PatronRequest patronRequest, Status from, Throwable error,
		Map<String, Object> auditData) {
		return addAuditEntry(patronRequest, from, ERROR,
			Optional.ofNullable(error.getMessage()), Optional.ofNullable(auditData));
	}
	
//	@Transactional
	protected <T> Mono<T> createAuditEntryPublisher(T context, BiConsumer<T, PatronRequestAudit.PatronRequestAuditBuilder> consumer, boolean propagate) {
		Mono<PatronRequestAudit> flow = Mono.just(PatronRequestAudit.builder())
			.map( builder -> builder
					.id(UUID.randomUUID())
					.auditDate(Instant.now()))
			.flatMap( builder -> {
				consumer.accept(context, builder);
				return buildAndSaveAuditMessage(builder);
			})
			.doOnError( t -> log.error("Error creating audit log entry", t) );
		
		// No propagation means we complete normally... But log the error regardless.
		flow =  propagate ? flow : flow.onErrorComplete();
		return flow.thenReturn(context);
	}
	
	@SuppressWarnings("unchecked")
//	@Transactional
	protected <T, P extends Publisher<T>> Function<P, P> withAuditMessage ( BiConsumer<T, PatronRequestAudit.PatronRequestAuditBuilder> consumer, boolean propagate) {
		
		return (currentFlow) -> {
			var auditGenFlow = Flux.from( currentFlow )
				.flatMap( item -> createAuditEntryPublisher(item, consumer, propagate));
			
			return (P)(Publishers.isSingle(currentFlow.getClass()) ? auditGenFlow.single() : auditGenFlow);
		};
	}
	
//	@Transactional
	public <T, P extends Publisher<T>> Function<P, P> withAuditMessage ( BiConsumer<T, PatronRequestAudit.PatronRequestAuditBuilder> consumer) {
		return withAuditMessage(consumer, true);
	}


//	@Transactional
	public <T, P extends Publisher<T>> Function<P, P> withAuditMessageNoPropagateErrors( BiConsumer<T, PatronRequestAudit.PatronRequestAuditBuilder> consumer ) {
		return withAuditMessage(consumer, false);
	}
}
