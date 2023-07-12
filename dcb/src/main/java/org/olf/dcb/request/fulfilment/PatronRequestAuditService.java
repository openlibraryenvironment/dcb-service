package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestAuditService {

	private static final Logger log = LoggerFactory.getLogger(PatronRequestAuditService.class);

	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public PatronRequestAuditService(PatronRequestAuditRepository patronRequestAuditRepository) {
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	public Mono<PatronRequestAudit> audit(PatronRequestAudit patronRequestAudit, Boolean isErrorEntry) {
		return savePatronRequestAudit(patronRequestAudit).doOnSuccess(auditEntry -> log(auditEntry, isErrorEntry));
	}

	private void log(PatronRequestAudit auditEntry, Boolean isErrorEntry) {
		log.debug("{}: {}", isErrorEntry ? "Unsuccessful transition" : "Successful transition", auditEntry);
	}

	private Mono<PatronRequestAudit> savePatronRequestAudit(PatronRequestAudit patronRequestAudit) {
		return Mono.from(patronRequestAuditRepository.save(patronRequestAudit));
	}
}
