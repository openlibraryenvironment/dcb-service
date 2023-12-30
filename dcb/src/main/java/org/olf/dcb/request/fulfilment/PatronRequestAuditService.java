package org.olf.dcb.request.fulfilment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.Map;

@Singleton
public class PatronRequestAuditService {

	private static final Logger log = LoggerFactory.getLogger(PatronRequestAuditService.class);

	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public PatronRequestAuditService(PatronRequestAuditRepository patronRequestAuditRepository) {
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	private void log(PatronRequestAudit auditEntry) {
		log.debug("AUDIT LOG {}: {}", auditEntry.getToStatus() == Status.ERROR ? "Unsuccessful transition" : "Successful transition", auditEntry);
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest, Status from, Status to) {
		return addAuditEntry(patronRequest, from, to, Optional.empty(), Optional.empty());
	}

	public Mono<PatronRequestAudit> addAuditEntry(
		PatronRequest patronRequest, 
		Status from, 
		Status to, 
		Optional<String> message ) {
		return addAuditEntry(patronRequest,from,to,message,Optional.empty());
  }
        
	public Mono<PatronRequestAudit> addAuditEntry(
                PatronRequest patronRequest, 
                Status from, 
                Status to, 
                Optional<String> message, 
                Optional<Map<String,Object>> auditData) {

		var builder = PatronRequestAudit.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.auditDate(Instant.now())
			.fromStatus(from)
			.toStatus(to);

		message.ifPresent(value -> {
    	String trimmedValue = value.substring(0, Math.min(value.length(), 254));
    	builder.briefDescription(trimmedValue);
    });

		PatronRequestAudit pra = builder.build();

		return Mono.just(pra)
			.flatMap(auditEntry -> Mono.from(patronRequestAuditRepository.save(auditEntry)).cast(PatronRequestAudit.class))
			.doOnSuccess(this::log)
			.doOnError( error -> log.error("Error attempting to write audit for"+pra.toString(), error));
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest, String message) {
		return addAuditEntry(patronRequest, patronRequest.getStatus(), Status.ERROR, Optional.ofNullable(message), Optional.empty());
	}
	
	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest, Throwable error) {
		return addErrorAuditEntry(patronRequest, patronRequest.getStatus(), error, null);
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest, Status from, Throwable error) {
                return addErrorAuditEntry(patronRequest,from,error,null);
        }

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest, Status from, Throwable error, Map<String,Object> auditData) {
		return addAuditEntry(patronRequest, from, Status.ERROR, Optional.ofNullable(error.getMessage()), Optional.ofNullable(auditData));
	}

  private String trimLongerThan(String s, int maxlen) {
		return s;
	}
}
